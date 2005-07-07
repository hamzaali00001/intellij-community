/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 30, 2002
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.aspects.psi.PsiIntertypeField;
import com.intellij.aspects.psi.PsiIntertypeMethod;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.aspects.psi.PsiPrimitiveTypePattern;
import com.intellij.aspects.psi.gen.PsiRegularMethodPattern;
import com.intellij.codeInsight.CodeInsightColors;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

public class HighlightUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil");
  private static final String SYMBOL_IS_PRIVATE = "''{0}'' has private access in ''{1}''";
  private static final String SYMBOL_IS_PROTECTED = "''{0}'' has protected access in ''{1}''";
  private static final String SYMBOL_IS_PACKAGE_LOCAL = "''{0}'' is not public in ''{1}''. Cannot be accessed from outside package";
  private static final String PROBLEM_WITH_STATIC = "Non-static {0} ''{1}'' cannot be referenced from a static context";
  public static final String INCOMPATIBLE_TYPES = "Incompatible types. Found: ''{1}'', required: ''{0}''";
  private static final String VARIABLE_ALREADY_DEFINED = "Variable ''{0}'' is already defined in the scope";
  private static final String RETURN_OUTSIDE_METHOD = "Return outside method";
  private static final String MISSING_RETURN_VALUE = "Missing return value";
  private static final String CANNOT_RETURN_WHEN_VOID = "Cannot return a value from a method with void result type";
  private static final String UNHANDLED_EXCEPTIONS = "Unhandled exceptions: ";
  private static final String UNHANDLED_EXCEPTION = "Unhandled exception: ";
  public static final String EXCEPTION_NEVER_THROWN_IN_TRY = "Exception ''{0}'' is never thrown in the corresponding try block";
  private static final Map<String, Set<String>> ourInterfaceIncompatibleModifiers;
  public static final Map<String, Set<String>> ourMethodIncompatibleModifiers;
  private static final Map<String, Set<String>> ourFieldIncompatibleModifiers;
  private static final Map<String, Set<String>> ourClassIncompatibleModifiers;
  private static final Map<String, Set<String>> ourClassInitializerIncompatibleModifiers;
  public static final String NO_DEFAULT_CONSTRUCTOR = "There is no default constructor available in ''{0}''";
  public static final String EXPRESSION_EXPECTED = "Expression expected";
  private static final Set<String> ourConstructorNotAllowedModifiers;
  public static final String INCONVERTIBLE_TYPE_CAST = "Inconvertible types; cannot cast ''{0}'' to ''{1}''";
  private static final String BINARY_OPERATOR_NOT_APPLICABLE = "Operator ''{0}'' cannot be applied to ''{1}'',''{2}''";

  private static final Key<String> HAS_OVERFLOW_IN_CHILD = Key.create("HAS_OVERFLOW_IN_CHILD");

  private HighlightUtil() {}

  static {
    ourClassIncompatibleModifiers = new THashMap<String, Set<String>>(8);
    Set<String> modifiers = new THashSet<String>(1);
    modifiers.add(PsiModifier.FINAL);
    ourClassIncompatibleModifiers.put(PsiModifier.ABSTRACT, modifiers);
    modifiers = new THashSet<String>(1);
    modifiers.add(PsiModifier.ABSTRACT);
    ourClassIncompatibleModifiers.put(PsiModifier.FINAL, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourClassIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourClassIncompatibleModifiers.put(PsiModifier.PRIVATE, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PROTECTED);
    ourClassIncompatibleModifiers.put(PsiModifier.PUBLIC, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PRIVATE);
    ourClassIncompatibleModifiers.put(PsiModifier.PROTECTED, modifiers);
    ourClassIncompatibleModifiers.put(PsiModifier.STRICTFP, Collections.EMPTY_SET);
    ourClassIncompatibleModifiers.put(PsiModifier.STATIC, Collections.EMPTY_SET);
    ourInterfaceIncompatibleModifiers = new THashMap<String, Set<String>>(7);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.ABSTRACT, Collections.EMPTY_SET);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.PRIVATE, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PROTECTED);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.PUBLIC, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.PROTECTED, modifiers);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.STRICTFP, Collections.EMPTY_SET);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.STATIC, Collections.EMPTY_SET);
    ourMethodIncompatibleModifiers = new THashMap<String, Set<String>>(10);
    modifiers = new THashSet<String>(6);
    modifiers.addAll(Arrays.asList(new String[]{PsiModifier.NATIVE, PsiModifier.STATIC, PsiModifier.FINAL, PsiModifier.PRIVATE, PsiModifier.STRICTFP,
                                                PsiModifier.SYNCHRONIZED}));
    ourMethodIncompatibleModifiers.put(PsiModifier.ABSTRACT, modifiers);
    modifiers = new THashSet<String>(2);
    modifiers.add(PsiModifier.ABSTRACT);
    modifiers.add(PsiModifier.STRICTFP);
    ourMethodIncompatibleModifiers.put(PsiModifier.NATIVE, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourMethodIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, modifiers);
    modifiers = new THashSet<String>(4);
    modifiers.add(PsiModifier.ABSTRACT);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourMethodIncompatibleModifiers.put(PsiModifier.PRIVATE, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PROTECTED);
    ourMethodIncompatibleModifiers.put(PsiModifier.PUBLIC, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PRIVATE);
    ourMethodIncompatibleModifiers.put(PsiModifier.PROTECTED, modifiers);
    modifiers = new THashSet<String>(1);
    modifiers.add(PsiModifier.ABSTRACT);
    ourMethodIncompatibleModifiers.put(PsiModifier.STATIC, modifiers);
    ourMethodIncompatibleModifiers.put(PsiModifier.SYNCHRONIZED, modifiers);
    ourMethodIncompatibleModifiers.put(PsiModifier.STRICTFP, modifiers);
    ourMethodIncompatibleModifiers.put(PsiModifier.FINAL, modifiers);
    ourFieldIncompatibleModifiers = new THashMap<String, Set<String>>(8);
    modifiers = new THashSet<String>(1);
    modifiers.add(PsiModifier.VOLATILE);
    ourFieldIncompatibleModifiers.put(PsiModifier.FINAL, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourFieldIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourFieldIncompatibleModifiers.put(PsiModifier.PRIVATE, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PROTECTED);
    ourFieldIncompatibleModifiers.put(PsiModifier.PUBLIC, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PUBLIC);
    ourFieldIncompatibleModifiers.put(PsiModifier.PROTECTED, modifiers);
    ourFieldIncompatibleModifiers.put(PsiModifier.STATIC, Collections.EMPTY_SET);
    ourFieldIncompatibleModifiers.put(PsiModifier.TRANSIENT, Collections.EMPTY_SET);
    modifiers = new THashSet<String>(1);
    modifiers.add(PsiModifier.FINAL);
    ourFieldIncompatibleModifiers.put(PsiModifier.VOLATILE, modifiers);

    ourClassInitializerIncompatibleModifiers = new THashMap<String, Set<String>>(1);
    ourClassInitializerIncompatibleModifiers.put(PsiModifier.STATIC, Collections.EMPTY_SET);

    ourConstructorNotAllowedModifiers = new THashSet<String>(6);
    ourConstructorNotAllowedModifiers.add(PsiModifier.ABSTRACT);
    ourConstructorNotAllowedModifiers.add(PsiModifier.STATIC);
    ourConstructorNotAllowedModifiers.add(PsiModifier.NATIVE);
    ourConstructorNotAllowedModifiers.add(PsiModifier.FINAL);
    ourConstructorNotAllowedModifiers.add(PsiModifier.STRICTFP);
    ourConstructorNotAllowedModifiers.add(PsiModifier.SYNCHRONIZED);
  }

  @Nullable
  public static String getIncompatibleModifier(String modifier,
                                               PsiModifierList modifierList,
                                               Map<String, Set<String>> incompatibleModifiersHash) {
    if (modifierList == null) return null;
    // modifier is always incompatible with itself
    PsiElement[] modifiers = modifierList.getChildren();
    int modifierCount = 0;
    for (PsiElement otherModifier : modifiers) {
      if (Comparing.equal(modifier, otherModifier.getText(), true)) modifierCount++;
    }
    if (modifierCount > 1) {
      return modifier;
    }

    Set<String> incompatibles = incompatibleModifiersHash.get(modifier);
    if (incompatibles == null) return null;
    for (final String incompatible : incompatibles) {
      if (modifierList.hasModifierProperty(incompatible)) {
        return incompatible;
      }
    }
    return null;
  }

  /**
   * make element protected/package local/public suggestion
   */
  static void registerAccessQuickFixAction(PsiMember refElement, PsiJavaCodeReferenceElement place, HighlightInfo errorResult) {
    if (refElement instanceof PsiCompiledElement) return;
    PsiModifierList modifierList = refElement.getModifierList();
    if (modifierList == null) return;

    PsiClass accessObjectClass = null;
    PsiElement scope = place;
    while (scope != null) {
      if (scope instanceof PsiClass) {
        accessObjectClass = (PsiClass)scope;
        break;
      }
      scope = scope.getParent();
    }

    PsiClass packageLocalClassInTheMiddle = getPackageLocalClassInTheMiddle(place);
    if (packageLocalClassInTheMiddle != null) {
      QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(packageLocalClassInTheMiddle, PsiModifier.PUBLIC, true, true), null);
      return;
    }

    try {
      PsiModifierList modifierListCopy = refElement.getManager().getElementFactory().createFieldFromText("int a;", null).getModifierList();
      modifierListCopy.setModifierProperty(PsiModifier.STATIC, modifierList.hasModifierProperty(PsiModifier.STATIC));
      String[] modifiers = new String[]{PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PUBLIC};
      int i = 0;
      if (refElement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        i = 1;
      }
      if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
        i = 2;
      }
      for (; i < modifiers.length; i++) {
        String modifier = modifiers[i];
        modifierListCopy.setModifierProperty(modifier, true);
        if (refElement.getManager().getResolveHelper().isAccessible(refElement, modifierListCopy, place, accessObjectClass)) {
          QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(refElement, modifier, true, true), null);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static PsiClass getPackageLocalClassInTheMiddle(PsiJavaCodeReferenceElement place) {
    if (place instanceof PsiReferenceExpression) {
      // check for package local classes in the middle
      PsiReferenceExpression expression = (PsiReferenceExpression)place;
      while (true) {
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiField) {
          PsiField field = (PsiField)resolved;
          PsiClass aClass = field.getContainingClass();
          if (aClass != null
              && aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
              && !aClass.getManager().arePackagesTheSame(aClass, place)) {

            return aClass;
          }
        }
        PsiExpression qualifier = expression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) break;
        expression = (PsiReferenceExpression)qualifier;
      }
    }
    return null;
  }

  //@top
  @Nullable
  static HighlightInfo checkInstanceOfApplicable(PsiInstanceOfExpression expression) {
    PsiExpression operand = expression.getOperand();
    PsiType checkType = expression.getCheckType().getType();
    PsiType operandType = operand.getType();
    if (checkType == null || operandType == null) return null;
    if (TypeConversionUtil.isPrimitiveAndNotNull(operandType)
        || TypeConversionUtil.isPrimitiveAndNotNull(checkType)
        || !TypeConversionUtil.areTypesConvertible(operandType, checkType)) {
      String message = MessageFormat.format(INCONVERTIBLE_TYPE_CAST,
                                            new Object[]{formatType(operandType), formatType(checkType)});
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               expression,
                                               message);
    }
    return null;
  }

  //@top
  @Nullable
  static HighlightInfo checkInconvertibleTypeCast(PsiTypeCastExpression expression) {
    PsiExpression operand = expression.getOperand();
    PsiType castType = expression.getCastType().getType();
    PsiType operandType = operand == null ? null : operand.getType();
    if (operandType != null
        && castType != null
        && !TypeConversionUtil.areTypesConvertible(operandType, castType)) {
      String message = MessageFormat.format(INCONVERTIBLE_TYPE_CAST,
                                            new Object[]{formatType(operandType), formatType(castType)});
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               expression,
                                               message);
    }
    return null;
  }

  //@top
  static HighlightInfo checkVariableExpected(PsiExpression expression) {
    HighlightInfo errorResult = null;
    PsiExpression lValue;
    if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)expression;
      lValue = assignment.getLExpression();
    }
    else if (PsiUtil.isIncrementDecrementOperation(expression)) {
      lValue = expression instanceof PsiPostfixExpression ?
               ((PsiPostfixExpression)expression).getOperand() :
               ((PsiPrefixExpression)expression).getOperand();
    }
    else {
      lValue = null;
    }
    if (lValue != null && !TypeConversionUtil.isLValue(lValue)) {
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                      lValue,
                                                      "Variable expected");
    }

    return errorResult;
  }

  //@top
  @Nullable
  static HighlightInfo checkAssignmentOperatorApplicable(PsiAssignmentExpression assignment) {
    PsiJavaToken operationSign = assignment.getOperationSign();
    IElementType eqOpSign = operationSign.getTokenType();
     IElementType opSign = convertEQtoOperation(eqOpSign);
    if (opSign == null) return null;
    HighlightInfo errorResult = null;
    if (!TypeConversionUtil.isBinaryOperatorApplicable(opSign, assignment.getLExpression(), assignment.getRExpression(), true)) {
      String operatorText = operationSign.getText().substring(0, operationSign.getText().length() - 1);
      String message = MessageFormat.format(BINARY_OPERATOR_NOT_APPLICABLE,
                                            new Object[]{
                                              operatorText,
                                              formatType(assignment.getLExpression().getType()),
                                              formatType(assignment.getRExpression().getType())
                                            });
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                      assignment,
                                                      message);
    }
    return errorResult;
  }

  public static IElementType convertEQtoOperation(IElementType eqOpSign) {
    IElementType opSign = null;
    if (eqOpSign == JavaTokenType.ANDEQ) {
      opSign = JavaTokenType.AND;
    }
    else if (eqOpSign == JavaTokenType.ASTERISKEQ) {
      opSign = JavaTokenType.ASTERISK;
    }
    else if (eqOpSign == JavaTokenType.DIVEQ) {
      opSign = JavaTokenType.DIV;
    }
    else if (eqOpSign == JavaTokenType.GTGTEQ) {
      opSign = JavaTokenType.GTGT;
    }
    else if (eqOpSign == JavaTokenType.GTGTGTEQ) {
      opSign = JavaTokenType.GTGTGT;
    }
    else if (eqOpSign == JavaTokenType.LTLTEQ) {
      opSign = JavaTokenType.LTLT;
    }
    else if (eqOpSign == JavaTokenType.MINUSEQ) {
      opSign = JavaTokenType.MINUS;
    }
    else if (eqOpSign == JavaTokenType.OREQ) {
      opSign = JavaTokenType.OR;
    }
    else if (eqOpSign == JavaTokenType.PERCEQ) {
      opSign = JavaTokenType.PERC;
    }
    else if (eqOpSign == JavaTokenType.PLUSEQ) {
      opSign = JavaTokenType.PLUS;
    }
    else if (eqOpSign == JavaTokenType.XOREQ) {
      opSign = JavaTokenType.XOR;
    }
    return opSign;
  }

  //@top
  @Nullable
  static HighlightInfo checkAssignmentCompatibleTypes(PsiAssignmentExpression assignment) {
    if (assignment.getOperationSign() == null || !"=".equals(assignment.getOperationSign().getText())) return null;
    PsiExpression lExpr = assignment.getLExpression();
    PsiExpression rExpr = assignment.getRExpression();
    if (rExpr == null) return null;
    PsiType lType = lExpr.getType();
    PsiType rType = rExpr.getType();
    if (rType == null) return null;

    HighlightInfo highlightInfo = checkAssignability(lType, rType, rExpr, assignment);
    if (highlightInfo != null) {
      PsiVariable leftVar = null;
      if (lExpr instanceof PsiReferenceExpression) {
        PsiElement element = ((PsiReferenceExpression)lExpr).resolve();
        if (element instanceof PsiVariable) {
          leftVar = (PsiVariable)element;
        }
      }
      if (leftVar != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new VariableTypeFix(leftVar, rType), null);
      }
    }
    return highlightInfo;
  }

  public static boolean isCastIntentionApplicable(PsiExpression expression, PsiType toType) {
    while (expression instanceof PsiTypeCastExpression || expression instanceof PsiParenthesizedExpression) {
      if (expression instanceof PsiTypeCastExpression) {
        expression = ((PsiTypeCastExpression)expression).getOperand();
      }
      if (expression instanceof PsiParenthesizedExpression) {
        expression = ((PsiParenthesizedExpression)expression).getExpression();
      }
    }
    if (expression == null) return false;
    PsiType rType = expression.getType();
    if (rType == null || toType == null) return false;
    return TypeConversionUtil.areTypesConvertible(rType, toType);
  }

  //@top
  @Nullable
  static HighlightInfo checkVariableInitializerType(PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    // array initalizer checked in checkArrayInitializerApplicable
    if (initializer == null || initializer instanceof PsiArrayInitializerExpression) return null;
    PsiType lType = variable.getType();
    PsiType rType = initializer.getType();
    int start = variable.getTypeElement().getTextRange().getStartOffset();
    int end = variable.getTextRange().getEndOffset();
    HighlightInfo highlightInfo = checkAssignability(lType, rType, initializer, new TextRange(start, end));
    if (highlightInfo != null) {
      QuickFixAction.registerQuickFixAction(highlightInfo, new VariableTypeFix(variable, rType), null);
    }
    return highlightInfo;
  }

  @Nullable
  public static HighlightInfo checkAssignability(PsiType lType,
                                                 PsiType rType,
                                                 PsiExpression expression,
                                                 PsiElement elementToHighlight) {
    TextRange textRange = elementToHighlight.getTextRange();
    return checkAssignability(lType, rType, expression, textRange);
  }

  @Nullable
  public static HighlightInfo checkAssignability(PsiType lType,
                                                 PsiType rType,
                                                 PsiExpression expression,
                                                 TextRange textRange) {
    if (expression == null) {
      if (TypeConversionUtil.isAssignable(lType, rType)) return null;
    }
    else if (TypeConversionUtil.areTypesAssignmentCompatible(lType, expression)) {
      return GenericsHighlightUtil.checkRawToGenericAssignment(lType, rType, expression);
    }

    Pair<String, String> typeInfo = createIncompatibleTypesDescriptionAndToolTip(lType, rType);
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, typeInfo.getFirst(),
                                                                  typeInfo.getSecond());
    if (rType != null && expression != null && isCastIntentionApplicable(expression, lType)) {
      QuickFixAction.registerQuickFixAction(errorResult, new AddTypeCastFix(lType, expression), null);
    }
    if (lType instanceof PsiClassType && expression != null) {
      QuickFixAction.registerQuickFixAction(errorResult, new WrapExpressionFix((PsiClassType)lType, expression), null);
    }
    return errorResult;
  }

  //@top
  @Nullable
  static HighlightInfo checkReturnStatementType(PsiReturnStatement statement) {
    PsiMethod method = null;
    PsiElement parent = statement.getParent();
    while (true) {
      if (parent instanceof PsiFile) break;
      if (parent instanceof PsiClassInitializer) break;
      if (parent instanceof PsiMethod) {
        method = (PsiMethod)parent;
        break;
      }
      parent = parent.getParent();
    }
    String description;
    int navigationShift = 0;
    HighlightInfo errorResult = null;
    if (method == null && !(parent instanceof JspFile)) {
      description = RETURN_OUTSIDE_METHOD;
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.RETURN_OUTSIDE_METHOD, statement, description);
    }
    else {
      PsiType returnType = method != null ? method.getReturnType() : null/*JSP page returns void*/;
      boolean isMethodVoid = returnType == null || PsiType.VOID == returnType;
      PsiExpression returnValue = statement.getReturnValue();
      if (returnValue != null) {
        if (isMethodVoid) {
          description = CANNOT_RETURN_WHEN_VOID;
          errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, description);
          QuickFixAction.registerQuickFixAction(errorResult, new MethodReturnFix(method, returnValue.getType(), false), null);
        }
        else {
          PsiType valueType = returnValue.getType();
          errorResult = checkAssignability(returnType, valueType, returnValue, statement);
          if (errorResult != null) {
            QuickFixAction.registerQuickFixAction(errorResult, new MethodReturnFix(method, returnValue.getType(), false), null);
          }
        }
        navigationShift = returnValue.getStartOffsetInParent();
      }
      else {
        if (!isMethodVoid) {
          description = MISSING_RETURN_VALUE;
          errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, description);
          QuickFixAction.registerQuickFixAction(errorResult, new MethodReturnFix(method, PsiType.VOID, false), null);
          navigationShift = PsiKeyword.RETURN.length();
        }
      }
    }
    if (errorResult != null) {
      errorResult.navigationShift = navigationShift;
    }
    return errorResult;
  }

  public static String getUnhandledExceptionsDescriptor(PsiClassType[] unhandledExceptions) {
    StringBuffer description = new StringBuffer();
    if (unhandledExceptions.length > 1) {
      description.append(UNHANDLED_EXCEPTIONS);
    }
    else {
      description.append(UNHANDLED_EXCEPTION);
    }

    for (int i = 0; i < unhandledExceptions.length; i++) {
      PsiClassType unhandledException = unhandledExceptions[i];
      if (i > 0) description.append(", ");
      description.append(formatType(unhandledException));
    }
    return description.toString();
  }

  //@top
  @Nullable
  static HighlightInfo checkVariableAlreadyDefined(PsiVariable variable) {
    boolean isIncorrect = false;
    PsiIdentifier identifier = variable.getNameIdentifier();
    String name = identifier.getText();
    if (variable instanceof PsiLocalVariable
        || variable instanceof PsiParameter && ((PsiParameter)variable).getDeclarationScope() instanceof PsiCatchSection
        || variable instanceof PsiParameter && ((PsiParameter)variable).getDeclarationScope() instanceof PsiForeachStatement) {
      PsiElement scope = PsiTreeUtil.getParentOfType(variable, PsiFile.class, PsiMethod.class, PsiClassInitializer.class);
      VariablesNotProcessor proc = new VariablesNotProcessor(variable, false);
      PsiScopesUtil.treeWalkUp(proc, identifier, scope);
      if (proc.size() > 0) {
        isIncorrect = true;
      }
    }
    else {
      PsiElement scope = variable.getParent();
      PsiElement[] children = scope.getChildren();
      for (PsiElement child : children) {
        if (child instanceof PsiVariable) {
          if (child.equals(variable)) continue;
          if (name.equals(((PsiVariable)child).getName())) {
            isIncorrect = true;
            break;
          }
        }
      }
    }

    if (isIncorrect) {
      String description = MessageFormat.format(VARIABLE_ALREADY_DEFINED, new Object[]{name});
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, identifier, description);
      QuickFixAction.registerQuickFixAction(highlightInfo, new ReuseVariableDeclarationFix(variable, identifier), null);
      return highlightInfo;
    }
    return null;
  }

  public static String formatClass(PsiClass aClass) {
    return formatClass(aClass, true);
  }

  public static String formatClass(PsiClass aClass, boolean fqn) {
    return PsiFormatUtil.formatClass(aClass,
                                     PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_ANONYMOUS_CLASS_VERBOSE |
                                     (fqn ? PsiFormatUtil.SHOW_FQ_NAME : 0));
  }

  public static String formatMethod(PsiMethod method) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                      PsiFormatUtil.SHOW_TYPE);
  }

  public static String formatType(PsiType type) {
    if (type == null) return "null";
    return type.getInternalCanonicalText();
  }

  //@top
  public static HighlightInfo checkUnhandledExceptions(PsiElement element, TextRange fixRange) {
    PsiClassType[] unhandledExceptions = ExceptionUtil.getUnhandledExceptions(element);
    HighlightInfo errorResult = null;
    if (unhandledExceptions.length > 0) {
      if (fixRange == null) {
        fixRange = element.getTextRange();
      }
      HighlightInfoType highlightType = getUnhandledExceptionHighlightType(element.getContainingFile());
      if (highlightType == null) return null;
      errorResult = HighlightInfo.createHighlightInfo(highlightType,
                                                      fixRange,
                                                      getUnhandledExceptionsDescriptor(unhandledExceptions));
      QuickFixAction.registerQuickFixAction(errorResult, new AddExceptionToCatchFix(), null);
      QuickFixAction.registerQuickFixAction(errorResult, new AddExceptionToThrowsFix(element), null);
      QuickFixAction.registerQuickFixAction(errorResult, new SurroundWithTryCatchAction(element), null);
      if (unhandledExceptions.length == 1) {
        QuickFixAction.registerQuickFixAction(errorResult, new GeneralizeCatchFix(element, unhandledExceptions[0]), null);
      }
    }
    return errorResult;
  }

  private static HighlightInfoType getUnhandledExceptionHighlightType(final PsiFile containingFile) {
    if (!(containingFile instanceof JspFile)) {
      return HighlightInfoType.UNHANDLED_EXCEPTION;
    }
    JspFile jspFile = (JspFile)containingFile;
    PsiFile errorPage = jspFile.getErrorPage();
    // highlight unhandled exception as warning if there is no errorPage directive
    if (errorPage == null) {
      return HighlightInfoType.WARNING;
    }
    else {
      return null;
    }
  }

  //@top
  @Nullable
  static HighlightInfo checkBreakOutsideLoop(PsiBreakStatement statement) {
    if (statement.getLabelIdentifier() == null) {
      if (new PsiMatcherImpl(statement).ancestor(PsiMatcherExpression.ENCLOSING_LOOP_OR_SWITCH).getElement() == null) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 statement,
                                                 "Break outside switch or loop");
      }
    }
    else {
      // todo labeled
    }
    return null;
  }

  //@top
  @Nullable
  static HighlightInfo checkContinueOutsideLoop(PsiContinueStatement statement) {
    if (statement.getLabelIdentifier() == null) {
      if (new PsiMatcherImpl(statement).ancestor(PsiMatcherExpression.ENCLOSING_LOOP).getElement() == null) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 statement,
                                                 "Continue outside of loop");
      }
    }
    else {
      PsiStatement exitedStatement = statement.findContinuedStatement();
      if (exitedStatement == null) return null;
      if (!(exitedStatement instanceof PsiForStatement)
          && !(exitedStatement instanceof PsiWhileStatement)
          && !(exitedStatement instanceof PsiDoWhileStatement)
          && !(exitedStatement instanceof PsiForeachStatement)) {
        String description = MessageFormat.format("Not a loop label: ''{0}''",
                                                  new Object[]{statement.getLabelIdentifier().getText()});
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 statement,
                                                 description);
      }
    }
    return null;
  }

  //@top
  static HighlightInfo checkIllegalModifierCombination(PsiKeyword keyword, PsiModifierList modifierList) {
    String modifier = keyword.getText();
    String incompatible = getIncompatibleModifier(modifier, modifierList);

    HighlightInfo highlightInfo = null;
    if (incompatible != null) {
      String message = MessageFormat.format("Illegal combination of modifiers: ''{0}'' and ''{1}''", new Object[]{modifier, incompatible});
      highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, keyword, message);
      QuickFixAction.registerQuickFixAction(highlightInfo, new ModifierFix(modifierList, modifier, false), null);
    }
    return highlightInfo;
  }

  @Nullable
  public static Map<String, Set<String>> getIncompatibleModifierMap(PsiModifierList modifierList) {
    PsiElement parent = modifierList.getParent();
    if (parent == null || PsiUtil.hasErrorElementChild(parent)) return null;
    return parent instanceof PsiClass ?
           ((PsiClass)parent).isInterface() ? ourInterfaceIncompatibleModifiers : ourClassIncompatibleModifiers
           : parent instanceof PsiMethod ? ourMethodIncompatibleModifiers
             : parent instanceof PsiVariable ? ourFieldIncompatibleModifiers
               : parent instanceof PsiClassInitializer ? ourClassInitializerIncompatibleModifiers
                 : parent instanceof PsiPointcutDef ? ourMethodIncompatibleModifiers
                   : null;
  }

  @Nullable
  public static String getIncompatibleModifier(String modifier, PsiModifierList modifierList) {
    PsiElement parent = modifierList.getParent();
    if (parent == null || PsiUtil.hasErrorElementChild(parent)) return null;
    final Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(modifierList);
    if(incompatibleModifierMap == null) return null;
    return getIncompatibleModifier(modifier, modifierList, incompatibleModifierMap);
  }

  //@top
  @Nullable
  public static HighlightInfo checkNotAllowedModifier(PsiKeyword keyword, PsiModifierList modifierList) {
    PsiElement modifierOwner = modifierList.getParent();
    if (modifierOwner == null) return null;
    if (PsiUtil.hasErrorElementChild(modifierOwner)) return null;
    String modifier = keyword.getText();
    final Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(modifierList);
    if(incompatibleModifierMap == null) return null;
    Set<String> incompatibles = incompatibleModifierMap.get(modifier);
    boolean isAllowed = true;
    PsiElement modifierOwnerParent = modifierOwner.getParent();
    if (modifierOwner instanceof PsiClass) {
      PsiClass aClass = (PsiClass)modifierOwner;
      if (aClass.isInterface()) {
        if (PsiModifier.STATIC.equals(modifier)
            || PsiModifier.PRIVATE.equals(modifier)
            || PsiModifier.PROTECTED.equals(modifier)
            || PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiClass;
        }
      }
      else {
        if (PsiModifier.PUBLIC.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiJavaFile
                      || modifierOwnerParent instanceof PsiClass;
        }
        else if (PsiModifier.STATIC.equals(modifier)
                 || PsiModifier.PRIVATE.equals(modifier)
                 || PsiModifier.PROTECTED.equals(modifier)
                 || PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiClass;
        }

        if (aClass.isEnum()) {
          isAllowed &= !(PsiModifier.FINAL.equals(modifier) || PsiModifier.ABSTRACT.equals(modifier));
        }
      }
    }
    else if (modifierOwner instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)modifierOwner;
      isAllowed = !(method.isConstructor() && ourConstructorNotAllowedModifiers.contains(modifier));
      if ((method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED))
          && method.isConstructor()
          && method.getContainingClass() != null
          && method.getContainingClass().isEnum()) {
        isAllowed = false;
      }

      if (PsiModifier.PRIVATE.equals(modifier)
          || PsiModifier.PROTECTED.equals(modifier)
          || PsiModifier.TRANSIENT.equals(modifier)
          || PsiModifier.STRICTFP.equals(modifier)
          || PsiModifier.SYNCHRONIZED.equals(modifier)
      ) {
        boolean notInterface = modifierOwnerParent instanceof PsiClass && !((PsiClass)modifierOwnerParent).isInterface();
        isAllowed &= notInterface & !(modifierOwner instanceof PsiIntertypeMethod && PsiModifier.PROTECTED.equals(modifier));
      }
    }
    else if (modifierOwner instanceof PsiField) {
      if (PsiModifier.PRIVATE.equals(modifier)
          || PsiModifier.PROTECTED.equals(modifier)
          || PsiModifier.TRANSIENT.equals(modifier)
          || PsiModifier.STRICTFP.equals(modifier)
          || PsiModifier.SYNCHRONIZED.equals(modifier)
      ) {
        boolean isInterface = modifierOwnerParent instanceof PsiClass && !((PsiClass)modifierOwnerParent).isInterface();
        isAllowed = isInterface & !(modifierOwner instanceof PsiIntertypeField && PsiModifier.PROTECTED.equals(modifier));
      }
    }
    else if (modifierOwner instanceof PsiClassInitializer) {
      isAllowed = PsiModifier.STATIC.equals(modifier);
    }
    else if (modifierOwner instanceof PsiLocalVariable || modifierOwner instanceof PsiParameter) {
      isAllowed = PsiModifier.FINAL.equals(modifier);
    }
    else if (modifierOwner instanceof PsiPointcutDef) {
      isAllowed =
      PsiModifier.FINAL.equals(modifier) || PsiModifier.ABSTRACT.equals(modifier) ||
      PsiModifier.PUBLIC.equals(modifier) || PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PACKAGE_LOCAL.equals(modifier);
    }

    isAllowed &= incompatibles != null;
    if (!isAllowed) {
      String message = MessageFormat.format("Modifier ''{0}'' not allowed here", new Object[]{modifier});
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                      keyword,
                                                                      message);
      QuickFixAction.registerQuickFixAction(highlightInfo, new ModifierFix(modifierList, modifier, false), null);
      return highlightInfo;
    }
    return null;
  }

  //@top
  @Nullable
  static HighlightInfo checkLiteralExpressionParsingError(PsiLiteralExpression expression) {
    String error = expression.getParsingError();
    if (error != null) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               expression,
                                               error);
    }
    return null;
  }

  //@top
  @Nullable
  static HighlightInfo checkMustBeBoolean(PsiExpression expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof PsiIfStatement
        || parent instanceof PsiWhileStatement
        || parent instanceof PsiForStatement && expr.equals(((PsiForStatement)parent).getCondition())
        || parent instanceof PsiDoWhileStatement && expr.equals(((PsiDoWhileStatement)parent).getCondition())
    ) {
      if (expr.getNextSibling() instanceof PsiErrorElement) return null;

      PsiType type = expr.getType();
      if (!TypeConversionUtil.isBooleanType(type)) {
        Pair<String, String> typeInfo = createIncompatibleTypesDescriptionAndToolTip(PsiType.BOOLEAN, type);
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expr, typeInfo.getFirst(), typeInfo.getSecond());
      }
    }
    return null;
  }

  //@top
  @Nullable
  static HighlightInfo checkExceptionThrownInTry(PsiParameter parameter) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiCatchSection)) return null;
    PsiTryStatement statement = ((PsiCatchSection)declarationScope).getTryStatement();
    PsiClassType[] classes = ExceptionUtil.collectUnhandledExceptions(statement.getTryBlock(), statement.getTryBlock());
    if (classes == null) classes = PsiClassType.EMPTY_ARRAY;

    PsiType caughtType = parameter.getType();
    if (!(caughtType instanceof PsiClassType)) return null;
    if (ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)caughtType)) return null;

    for (PsiClassType exceptionType : classes) {
      if (exceptionType.isAssignableFrom(caughtType) || caughtType.isAssignableFrom(exceptionType)) return null;
    }

    String description = MessageFormat.format(EXCEPTION_NEVER_THROWN_IN_TRY, new Object[]{formatType(caughtType)});
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                  parameter,
                                                                  description);

    QuickFixAction.registerQuickFixAction(errorResult, new DeleteCatchFix(parameter), null);
    return errorResult;
  }

  //@top
  @Nullable
  static HighlightInfo checkNotAStatement(PsiStatement statement) {
    if (!PsiUtil.isStatement(statement)) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               statement,
                                               "Not a statement");
    }
    return null;
  }

  //@top
  public static HighlightInfo checkSwitchSelectorType(PsiSwitchStatement statement) {
    PsiExpression expression = statement.getExpression();
    HighlightInfo errorResult = null;
    if (expression != null && expression.getType() != null) {
      PsiType type = expression.getType();
      if (!isValidTypeForSwitchSelector(type)) {
        String message = MessageFormat.format(INCOMPATIBLE_TYPES,
                                              new Object[]{
                                                "byte, char, short or int"
                                                , formatType(type)
                                              });
        errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                        expression,
                                                        message);
        if (PsiType.LONG == type
            || PsiType.FLOAT == type
            || PsiType.DOUBLE == type) {
          QuickFixAction.registerQuickFixAction(errorResult, new AddTypeCastFix(PsiType.INT, expression), null);
        }
      }
    }
    return errorResult;
  }

  private static boolean isValidTypeForSwitchSelector(PsiType type) {
    if (TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.INT_RANK) return true;
    if (type instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass == null) return false;
      if (psiClass.isEnum()) {
        return true;
      }
    }
    return false;
  }

  //@top
  @Nullable
  static HighlightInfo checkBinaryOperatorApplicable(PsiBinaryExpression expression) {
    PsiExpression lOperand = expression.getLOperand();
    PsiExpression rOperand = expression.getROperand();
    PsiJavaToken operationSign = expression.getOperationSign();
    if (!TypeConversionUtil.isBinaryOperatorApplicable(operationSign.getTokenType(), lOperand, rOperand, false)) {
      String message = MessageFormat.format(BINARY_OPERATOR_NOT_APPLICABLE,
                                            new Object[]{
                                              operationSign.getText(),
                                              formatType(lOperand.getType()),
                                              formatType(rOperand.getType())
                                            });
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, message);
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkUnaryOperatorApplicable(PsiJavaToken token, PsiExpression expression) {
    if (token != null
        && expression != null
        && !TypeConversionUtil.isUnaryOperatorApplicable(token, expression)) {
      PsiType type = expression.getType();
      if (type == null) return null;
      String message = MessageFormat.format("Operator ''{0}'' cannot be applied to ''{1}''",
                                            new Object[]{
                                              token.getText(),
                                              formatType(type)
                                            });
      PsiElement parentExpr = token.getParent();
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parentExpr, message);
      if (parentExpr instanceof PsiPrefixExpression && token.getTokenType() == JavaTokenType.EXCL) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new NegationBroadScopeFix((PsiPrefixExpression)parentExpr), null);
      }
      return highlightInfo;
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkThisOrSuperExpressionInIllegalContext(PsiExpression expr, PsiJavaCodeReferenceElement qualifier) {
    if (expr instanceof PsiSuperExpression && !(expr.getParent() instanceof PsiReferenceExpression)) {
      // like in 'Object o = super;'
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               expr.getTextRange().getEndOffset(),
                                               expr.getTextRange().getEndOffset() + 1,
                                               "'.' expected");
    }
    PsiClass aClass = qualifier == null ? null : (PsiClass)qualifier.resolve();
    if (aClass != null && aClass.isInterface()) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, qualifier, HighlightClassUtil.CLASS_EXPECTED);
    }
    PsiType type = expr.getType();
    if (type == null) return null;
    PsiClass referencedClass = PsiUtil.resolveClassInType(type);
    if (referencedClass == null) return null;
    if (HighlightClassUtil.hasEnclosingInstanceInScope(referencedClass, expr)) return null;

    return HighlightClassUtil.reportIllegalEnclosingUsage(expr, null, aClass, expr);
  }

  static String buildProblemWithStaticDescription(PsiElement refElement) {
    String prefix = "";
    if (refElement instanceof PsiVariable) {
      prefix = "variable";
    }
    else if (refElement instanceof PsiMethod) {
      prefix = "method";
    }
    else if (refElement instanceof PsiClass) {
      prefix = "class";
    }
    else {
      LOG.assertTrue(false, "???" + refElement);
    }
    return MessageFormat.format(PROBLEM_WITH_STATIC,
                                new Object[]{prefix, HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY)});
  }

  static void registerStaticProblemQuickFixAction(PsiElement refElement, HighlightInfo errorResult, PsiJavaCodeReferenceElement place) {
    PsiModifierList modifierList = null;
    if (refElement instanceof PsiModifierListOwner) {
      modifierList = ((PsiModifierListOwner)refElement).getModifierList();
    }
    if (modifierList != null) {
      QuickFixAction.registerQuickFixAction(errorResult,
                                            new ModifierFix(modifierList, PsiModifier.STATIC, true), null);
    }
    // make context non static
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, null);
    if (staticParent != null) {
      QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(staticParent, PsiModifier.STATIC, false), null);
    }
  }

  static String buildProblemWithAccessDescription(PsiElement refElement, PsiJavaCodeReferenceElement reference, JavaResolveResult result) {
    String symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());

    if (((PsiModifierListOwner)refElement).hasModifierProperty(PsiModifier.PRIVATE)) {
      String containerName = HighlightMessageUtil.getSymbolName(refElement.getParent(), result.getSubstitutor());
      return MessageFormat.format(SYMBOL_IS_PRIVATE, new Object[]{symbolName, containerName});
    }
    else {
      if (((PsiModifierListOwner)refElement).hasModifierProperty(PsiModifier.PROTECTED)) {
        String containerName = HighlightMessageUtil.getSymbolName(refElement.getParent(), result.getSubstitutor());
        return MessageFormat.format(SYMBOL_IS_PROTECTED, new Object[]{symbolName, containerName});
      }
      else {
        PsiClass packageLocalClass = getPackageLocalClassInTheMiddle(reference);
        if (packageLocalClass != null) {
          refElement = packageLocalClass;
          symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());
        }
        if (((PsiModifierListOwner)refElement).hasModifierProperty(PsiModifier.PACKAGE_LOCAL) || packageLocalClass != null) {
          String containerName = HighlightMessageUtil.getSymbolName(refElement.getParent(), result.getSubstitutor());
          return MessageFormat.format(SYMBOL_IS_PACKAGE_LOCAL, new Object[]{symbolName, containerName});
        }
        else {
          String containerName = HighlightMessageUtil.getSymbolName(
            refElement instanceof PsiTypeParameter ? refElement.getParent().getParent() : refElement.getParent(), result.getSubstitutor());
          return MessageFormat.format("Cannot access ''{0}'' in ''{1}''", new Object[]{symbolName, containerName});
        }
      }
    }
  }

  static String buildArgTypesList(PsiExpressionList list) {
    StringBuffer buffer = new StringBuffer();
    buffer.append("(");
    PsiExpression[] args = list.getExpressions();
    for (int i = 0; i < args.length; i++) {
      if (i > 0) {
        buffer.append(", ");
      }
      PsiType argType = args[i].getType();
      buffer.append(argType != null ? formatType(argType) : "?");
    }
    buffer.append(")");
    return buffer.toString();
  }

  //@top
  @Nullable
  static HighlightInfo checkValidArrayAccessExpression(PsiExpression arrayExpression, PsiExpression indexExpression) {
    PsiType arrayExpressionType = arrayExpression == null ? null : arrayExpression.getType();
    if (arrayExpressionType != null && !(arrayExpressionType instanceof PsiArrayType)) {
      String description = MessageFormat.format("Array type expected; found: ''{0}''",
                                                new Object[]{formatType(arrayExpressionType)});
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, arrayExpression, description);
    }
    return checkAssignability(PsiType.INT, indexExpression.getType(), indexExpression, indexExpression);
  }

  //@top
  @Nullable
  public static HighlightInfo checkCatchParameterIsThrowable(PsiParameter parameter) {
    if (parameter.getDeclarationScope() instanceof PsiCatchSection) {
      PsiType type = parameter.getType();
      return checkMustBeThrowable(type, parameter, true);
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkArrayInitalizerCompatibleTypes(PsiExpression initializer) {
    if (!(initializer.getParent() instanceof PsiArrayInitializerExpression)) return null;
    PsiType elementType;
    PsiElement element = initializer.getParent();
    int dimensions = 0;
    while (element instanceof PsiArrayInitializerExpression) {
      element = element.getParent();
      dimensions++;
    }
    if (element instanceof PsiVariable) {
      elementType = ((PsiVariable)element).getType();
    }
    else if (element instanceof PsiNewExpression) {
      elementType = ((PsiNewExpression)element).getType();
    }
    else {
      // todo cdr illegal ?
      return null;
    }

    if (elementType == null) return null;
    PsiType type = elementType;
    for (; dimensions > 0; dimensions--) {
      if (!(type instanceof PsiArrayType)) break;
      type = ((PsiArrayType)type).getComponentType();
      if (type == null) break;
    }
    if (dimensions != 0) {
      return null;
      // we should get error when visit parent
    }

    if (type != null) {
      // compute initializer type based on initializer text
      PsiType initializerType = getInitializerType(initializer,
                                                   type instanceof PsiArrayType ? ((PsiArrayType)type).getComponentType() : type);
      if (initializerType instanceof PsiArrayType && type instanceof PsiArrayType) return null;
      // do not use PsiArrayInitializerExpression.getType() for computing expression type
      PsiExpression expression = initializer instanceof PsiArrayInitializerExpression ? null : initializer;
      return checkAssignability(type, initializerType, expression, initializer);
    }
    return null;
  }

  @Nullable
  public static PsiType getInitializerType(PsiExpression expression, PsiType compTypeForEmptyInitializer) {
    if (expression instanceof PsiArrayInitializerExpression) {
      PsiExpression[] initializers = ((PsiArrayInitializerExpression)expression).getInitializers();
      PsiType compType;
      if (initializers.length == 0) {
        compType = compTypeForEmptyInitializer;
      }
      else {
        PsiType componentType = compTypeForEmptyInitializer instanceof PsiArrayType
                                ? ((PsiArrayType)compTypeForEmptyInitializer).getComponentType()
                                : null;
        compType = getInitializerType(initializers[0], componentType);
      }
      return compType == null ? null : compType.createArrayType();
    }
    return expression.getType();
  }

  //@top
  @Nullable
  public static HighlightInfo checkExpressionRequired(PsiReferenceExpression expression) {
    if (expression.getNextSibling() instanceof PsiErrorElement) return null;
    PsiElement resolved = expression.advancedResolve(true).getElement();
    if (resolved == null) return null;
    PsiElement parent = expression.getParent();
    // String.class or String() are both correct
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiMethodCallExpression) return null;
    if (resolved instanceof PsiVariable) return null;
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                             expression,
                                             EXPRESSION_EXPECTED);
  }

  //@top
  @Nullable
  public static HighlightInfo checkArrayInitializerApplicable(PsiArrayInitializerExpression expression) {
    /*
    JLS 10.6 Array Initializers
    An array initializer may be specified in a declaration, or as part of an array creation expression
    */
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;
      if (variable.getType() == null || variable.getType() instanceof PsiArrayType) return null;
    }
    else if (parent instanceof PsiNewExpression) {
      return null;
    }
    else if (parent instanceof PsiArrayInitializerExpression) {
      return null;
    }
    HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                           expression,
                                                           EXPRESSION_EXPECTED);
    QuickFixAction.registerQuickFixAction(info, new AddNewArrayExpressionFix(expression), null);

    return info;
  }

  //@top
  @Nullable
  public static HighlightInfo checkCaseStatement(PsiSwitchLabelStatement statement) {
    PsiSwitchStatement switchStatement = statement.getEnclosingSwitchStatement();
    if (switchStatement == null) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               statement,
                                               "Case statement outside switch");
    }
    if (switchStatement.getBody() == null) return null;
    Object value = null;
    // check constant expression
    PsiExpression caseValue = statement.getCaseValue();
    PsiConstantEvaluationHelper evalHelper = statement.getManager().getConstantEvaluationHelper();
    boolean isEnumSwitch = false;
    if (!statement.isDefaultCase() && caseValue != null) {
      if (caseValue instanceof PsiReferenceExpression) {
        PsiElement element = ((PsiReferenceExpression)caseValue).resolve();
        if (element instanceof PsiEnumConstant) {
          isEnumSwitch = true;
          value = ((PsiEnumConstant)element).getName();
          if (!(((PsiReferenceExpression)caseValue).getQualifier() == null)) {
            String message = "An enum switch case label must be the unqualified name of an enumeration constant";
            return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                     caseValue,
                                                     message);
          }
        }
      }
      if (!isEnumSwitch) {
        value = evalHelper.computeConstantExpression(caseValue);
      }
      if (value == null) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 caseValue,
                                                 "Constant expression required");
      }
    }
    // Every case constant expression associated with a switch statement must be assignable (?5.2) to the type of the switch Expression.
    PsiExpression switchExpression = switchStatement.getExpression();
    if (caseValue != null && switchExpression != null) {
      HighlightInfo highlightInfo = checkAssignability(switchExpression.getType(), caseValue.getType(), caseValue, caseValue);
      if (highlightInfo != null) return highlightInfo;
    }

    // check duplicate
    PsiStatement[] statements = switchStatement.getBody().getStatements();
    for (PsiStatement st : statements) {
      if (st == statement) continue;
      if (!(st instanceof PsiSwitchLabelStatement)) continue;
      PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)st;
      if (labelStatement.isDefaultCase() != statement.isDefaultCase()) continue;
      PsiExpression caseExpr = labelStatement.getCaseValue();
      if (isEnumSwitch && caseExpr instanceof PsiReferenceExpression) {
        PsiElement element = ((PsiReferenceExpression)caseExpr).resolve();
        if (!(element instanceof PsiEnumConstant && Comparing.equal(((PsiEnumConstant)element).getName(), value))) continue;
      }
      else if (!Comparing.equal(evalHelper.computeConstantExpression(caseExpr), value)) continue;
      String description = statement.isDefaultCase() ?
                           "Duplicate default label" :
                           MessageFormat.format("Duplicate label ''{0}''", new Object[]{value});
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               value == null ? (PsiElement)statement : caseValue,
                                               description);
    }

    // must be followed with colon
    PsiElement lastChild = statement.getLastChild();
    while (lastChild instanceof PsiComment || lastChild instanceof PsiWhiteSpace) {
      lastChild = lastChild.getPrevSibling();
    }
    if (!(lastChild instanceof PsiJavaToken && ((PsiJavaToken)lastChild).getTokenType() == JavaTokenType.COLON)) {
      String description = MessageFormat.format("''{0}'' expected", new Object[]{":"});

      int start = statement.getTextRange().getEndOffset();
      int end = statement.getTextRange().getEndOffset() + 1;
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                      start, end,
                                                                      description);
      char[] chars = statement.getContainingFile().textToCharArray();
      assert chars != null;
      highlightInfo.isAfterEndOfLine = end >= chars.length || chars[start] == '\n' || chars[start] == '\r';
      return highlightInfo;
    }
    return null;
  }

  //@top
  /**
   * see JLS 8.3.2.3
   */
  @Nullable
  public static HighlightInfo checkIllegalForwardReferenceToField(PsiReferenceExpression expression, PsiField referencedField) {
    PsiClass containingClass = referencedField.getContainingClass();
    if (containingClass == null) return null;
    if (expression.getContainingFile() != referencedField.getContainingFile()) return null;
    if (expression.getTextRange().getStartOffset() >= referencedField.getTextRange().getEndOffset()) return null;
    // only simple reference can be illegal
    if (expression.getQualifierExpression() != null) return null;
    PsiField initField = findEnclosingFieldInitializer(expression);
    PsiClassInitializer classInitializer = findParentClassInitializer(expression);
    if (initField == null && classInitializer == null) return null;
    // instance initializers may access static fields
    boolean isStaticClassInitializer = classInitializer != null && classInitializer.hasModifierProperty(PsiModifier.STATIC);
    boolean isStaticInitField = initField != null && initField.hasModifierProperty(PsiModifier.STATIC);
    boolean inStaticContext = isStaticInitField || isStaticClassInitializer;
    if (!inStaticContext && referencedField.hasModifierProperty(PsiModifier.STATIC)) return null;
    if (PsiUtil.isOnAssignmentLeftHand(expression) && !PsiUtil.isAccessedForReading(expression)) return null;
    if (!containingClass.getManager().areElementsEquivalent(containingClass, PsiTreeUtil.getParentOfType(expression, PsiClass.class))) return null;
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                             expression,
                                             "Illegal forward reference");
  }

  /**
   * @return field that has initializer with this element as subexpression or null if not found
   */
  @Nullable
  static PsiField findEnclosingFieldInitializer(PsiElement element) {
    while (element != null) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiField) {
        PsiField field = (PsiField)parent;
        if (element == field.getInitializer()) return field;
        if (field instanceof PsiEnumConstant && element == ((PsiEnumConstant)field).getArgumentList()) return field;
      }
      if (element instanceof PsiClass || element instanceof PsiMethod) return null;
      element = parent;
    }
    return null;
  }

  @Nullable
  private static PsiClassInitializer findParentClassInitializer(PsiElement element) {
    while (element != null) {
      if (element instanceof PsiClassInitializer) return (PsiClassInitializer)element;
      if (element instanceof PsiClass || element instanceof PsiMethod) return null;
      element = element.getParent();
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkIllegalType(PsiTypeElement typeElement) {
    if (typeElement == null || typeElement.getParent() instanceof PsiTypeElement) return null;

    PsiType type = typeElement.getType();
    PsiType componentType = type.getDeepComponentType();
    if (componentType instanceof PsiClassType) {
      PsiClass aClass = PsiUtil.resolveClassInType(componentType);
      if (aClass == null) {
        String canonicalText = type.getCanonicalText();
        String description = MessageFormat.format("Unknown class: ''{0}''", new Object[]{canonicalText});
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 typeElement,
                                                 description);
      }
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkIllegalVoidType(PsiKeyword type) {
    if (!PsiKeyword.VOID.equals(type.getText())) return null;

    PsiElement parent = type.getParent();
    if (parent instanceof PsiTypeElement) {
      PsiElement typeOwner = parent.getParent();
      if (typeOwner instanceof PsiMethod) {
        if (((PsiMethod)typeOwner).getReturnTypeElement() == parent) return null;
      }
      else if (typeOwner instanceof PsiClassObjectAccessExpression
               && TypeConversionUtil.isVoidType(((PsiClassObjectAccessExpression)typeOwner).getOperand().getType())) {
        // like in Class c = void.class;
        return null;
      }
      else if (typeOwner != null && PsiUtil.hasErrorElementChild(typeOwner)) {
        // do not highlight incomplete declarations
        return null;
      }
      else if (typeOwner instanceof PsiCodeFragment) {
        if (typeOwner.getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null) return null;
      }
    }
    else if (parent instanceof PsiPrimitiveTypePattern) {
      PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiRegularMethodPattern) {
        if (((PsiRegularMethodPattern)pparent).getReturnTypePattern() == parent) return null;
      }
    }

    String description = MessageFormat.format("Illegal type: ''{0}''", new Object[]{PsiKeyword.VOID});
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                             type,
                                             description);
  }

  //@top
  @Nullable
  public static HighlightInfo checkMemberReferencedBeforeConstructorCalled(PsiElement expression) {
    String resolvedName;
    PsiClass referencedClass;
    if (expression.getParent() instanceof PsiJavaCodeReferenceElement) return null;
    if (expression instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolved = ((PsiJavaCodeReferenceElement)expression).advancedResolve(true).getElement();

      if (resolved instanceof PsiField) {
        PsiField referencedField = (PsiField)resolved;
        if (referencedField.hasModifierProperty(PsiModifier.STATIC)) return null;
        referencedClass = referencedField.getContainingClass();
        resolvedName =
        PsiFormatUtil.formatVariable(referencedField, PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME, PsiSubstitutor.EMPTY);
      }
      else if (resolved instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)resolved;
        if (method.hasModifierProperty(PsiModifier.STATIC)) return null;
        if (PsiKeyword.SUPER.equals(expression.getText()) || PsiKeyword.THIS.equals(expression.getText())) return null;
        referencedClass = method.getContainingClass();
        resolvedName =
        PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME, 0);
      }
      else if (resolved instanceof PsiClass) {
        PsiClass aClass = (PsiClass)resolved;
        if (aClass.hasModifierProperty(PsiModifier.STATIC)) return null;
        referencedClass = aClass.getContainingClass();
        if (referencedClass == null) return null;
        resolvedName = PsiFormatUtil.formatClass(aClass, PsiFormatUtil.SHOW_NAME);
      }
      else {
        return null;
      }
    }
    else if (expression instanceof PsiThisExpression) {
      PsiType type = ((PsiThisExpression)expression).getType();
      referencedClass = PsiUtil.resolveClassInType(type);
      resolvedName = referencedClass == null
                     ? null
                     : PsiFormatUtil.formatClass(referencedClass, PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME);
    }
    else {
      return null;
    }

    // check if expression inside super()/this() call
    PsiElement element = expression.getParent();
    while (element instanceof PsiExpression || element instanceof PsiExpressionList || element instanceof PsiAnonymousClass) {
      if (isSuperOrThisMethodCall(element)) {
        PsiElement parentClass = new PsiMatcherImpl(element)
          .parent(PsiMatcherImpl.hasClass(PsiExpressionStatement.class))
          .parent(PsiMatcherImpl.hasClass(PsiCodeBlock.class))
          .parent(PsiMatcherImpl.hasClass(PsiMethod.class))
          .dot(PsiMatcherImpl.isConstructor(true))
          .parent(PsiMatcherImpl.hasClass(PsiClass.class))
          .getElement();
        if (parentClass == null) {
          return null;
        }

        // only this class/superclasses instance methods are not allowed to call
        PsiClass aClass = (PsiClass)parentClass;
        // field or method should be declared in this class or super
        if (!InheritanceUtil.isInheritorOrSelf(aClass, referencedClass, true)) return null;
        // and point to our instance
        if (expression instanceof PsiReferenceExpression
            && !thisOrSuperReference(((PsiReferenceExpression)expression).getQualifierExpression(), aClass)) {
          return null;
        }
        String description = MessageFormat.format("Cannot reference ''{0}'' before supertype constructor has been called",
                                                  new Object[]{resolvedName});
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 expression,
                                                 description);
      }
      element = element.getParent();
    }
    return null;
  }

  static boolean isSuperOrThisMethodCall(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) return false;
    PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)element).getMethodExpression();
    if (methodExpression == null) return false;
    String name = methodExpression.getReferenceName();
    return PsiKeyword.SUPER.equals(name) || PsiKeyword.THIS.equals(name);
  }

  private static boolean thisOrSuperReference(PsiExpression qualifierExpression, PsiClass aClass) {
    if (qualifierExpression == null) return true;
    PsiJavaCodeReferenceElement qualifier;
    if (qualifierExpression instanceof PsiThisExpression) {
      qualifier = ((PsiThisExpression)qualifierExpression).getQualifier();
    }
    else if (qualifierExpression instanceof PsiSuperExpression) {
      qualifier = ((PsiSuperExpression)qualifierExpression).getQualifier();
    }
    else {
      return false;
    }
    if (qualifier == null) return true;
    PsiElement resolved = qualifier.resolve();
    if (!(resolved instanceof PsiClass)) return false;
    return InheritanceUtil.isInheritorOrSelf(aClass, (PsiClass)resolved, true);
  }

  //@top
  @Nullable
  public static HighlightInfo checkLabelWithoutStatement(PsiLabeledStatement statement) {
    if (statement.getStatement() == null) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               statement,
                                               "Label without statement");
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkLabelAlreadyInUse(PsiLabeledStatement statement) {
    PsiIdentifier identifier = statement.getLabelIdentifier();
    if (identifier == null) return null;
    String text = identifier.getText();
    PsiElement element = statement;
    while (element != null) {
      if (element instanceof PsiMethod || element instanceof PsiClass) break;
      if (element instanceof PsiLabeledStatement
          && element != statement
          && ((PsiLabeledStatement)element).getLabelIdentifier() != null
          && Comparing.equal(((PsiLabeledStatement)element).getLabelIdentifier().getText(), text)) {
        String description = MessageFormat.format("Label ''{0}'' already in use",
                                                  new Object[]{text});
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 identifier,
                                                 description);
      }
      element = element.getParent();
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkUnclosedComment(PsiComment comment) {
    if (!(comment instanceof PsiDocComment) && !(comment.getTokenType() == JavaTokenType.C_STYLE_COMMENT)) return null;
    if (!comment.getText().endsWith("*/")) {
      int start = comment.getTextRange().getEndOffset() - 1;
      int end = start + 1;
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               start, end,
                                               "Unclosed comment");
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkSillyAssignment(PsiAssignmentExpression assignment) {
    if (!DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile(assignment).isToolEnabled(HighlightDisplayKey.SILLY_ASSIGNMENT)) {
      return null;
    }
    if (InspectionManagerEx.inspectionResultSuppressed(assignment, HighlightDisplayKey.SILLY_ASSIGNMENT.getID())) return null;

    if (assignment.getOperationSign().getTokenType() != JavaTokenType.EQ) return null;
    PsiExpression lExpression = assignment.getLExpression();
    PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return null;
    lExpression = PsiUtil.deparenthesizeExpression(lExpression);
    rExpression = PsiUtil.deparenthesizeExpression(rExpression);
    if (!(lExpression instanceof PsiReferenceExpression) || !(rExpression instanceof PsiReferenceExpression)) return null;
    PsiReferenceExpression lRef = (PsiReferenceExpression)lExpression;
    PsiReferenceExpression rRef = (PsiReferenceExpression)rExpression;
    PsiManager manager = assignment.getManager();
    if (!sameInstanceReferences(lRef, rRef, manager)) return null;
    HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.SILLY_ASSIGNMENT,
                                                                    assignment,
                                                                    "Silly assignment");
    List<IntentionAction> options = new ArrayList<IntentionAction>();
    options.add(new SwitchOffToolAction(HighlightDisplayKey.SILLY_ASSIGNMENT));
    options.add(new AddNoInspectionCommentAction(HighlightDisplayKey.SILLY_ASSIGNMENT, assignment));
    options.add(new AddNoInspectionDocTagAction(HighlightDisplayKey.SILLY_ASSIGNMENT, assignment));
    options.add(new AddNoInspectionForClassAction(HighlightDisplayKey.SILLY_ASSIGNMENT, assignment));
    options.add(new AddNoInspectionAllForClassAction(assignment));
    options.add(new AddSuppressWarningsAnnotationAction(HighlightDisplayKey.SILLY_ASSIGNMENT, assignment));
    options.add(new AddSuppressWarningsAnnotationForClassAction(HighlightDisplayKey.SILLY_ASSIGNMENT, assignment));
    options.add(new AddSuppressWarningsAnnotationForAllAction(assignment));
    QuickFixAction.registerQuickFixAction(highlightInfo, new EmptyIntentionAction(HighlightDisplayKey.getDisplayNameByKey(HighlightDisplayKey.SILLY_ASSIGNMENT), options), options);
    return highlightInfo;
  }

  /**
   * @return true if both expressions resolve to the same variable/class or field in the same instance of the class
   */
  private static boolean sameInstanceReferences(PsiReferenceExpression lRef, PsiReferenceExpression rRef, PsiManager manager) {
    PsiElement lResolved = lRef.resolve();
    PsiElement rResolved = rRef.resolve();
    if (!manager.areElementsEquivalent(lResolved, rResolved)) return false;

    PsiExpression lQualifier = lRef.getQualifierExpression();
    PsiExpression rQualifier = rRef.getQualifierExpression();
    if (lQualifier instanceof PsiReferenceExpression && rQualifier instanceof PsiReferenceExpression) {
      return sameInstanceReferences((PsiReferenceExpression)lQualifier, (PsiReferenceExpression)rQualifier, manager);
    }
    if (Comparing.equal(lQualifier, rQualifier)) return true;
    boolean lThis = lQualifier == null || lQualifier instanceof PsiThisExpression;
    boolean rThis = rQualifier == null || rQualifier instanceof PsiThisExpression;
    return lThis && rThis;
  }

  //@top
  @Nullable
  public static HighlightInfo checkExceptionAlreadyCaught(PsiJavaCodeReferenceElement element, PsiElement resolved) {
    if (!(resolved instanceof PsiClass)) return null;
    PsiClass catchClass = (PsiClass)resolved;
    if (!(element.getParent() instanceof PsiTypeElement)) return null;
    PsiElement catchParameter = element.getParent().getParent();
    if (!(catchParameter instanceof PsiParameter)
        || !(((PsiParameter)catchParameter).getDeclarationScope() instanceof PsiCatchSection)) {
      return null;
    }
    PsiCatchSection catchSection = (PsiCatchSection)((PsiParameter)catchParameter).getDeclarationScope();
    PsiTryStatement statement = catchSection.getTryStatement();
    PsiCatchSection[] catchSections = statement.getCatchSections();
    int i = ArrayUtil.find(catchSections, catchSection);
    for (i--; i >= 0; i--) {
      PsiCatchSection section = catchSections[i];
      PsiType type = section.getCatchType();
      PsiClass upCatchClass = PsiUtil.resolveClassInType(type);
      if (upCatchClass == null) continue;
      if (InheritanceUtil.isInheritorOrSelf(catchClass, upCatchClass, true)) {
        String description = MessageFormat.format("Exception ''{0}'' has already been caught",
                                                  new Object[]{
                                                    PsiFormatUtil.formatClass(catchClass,
                                                                              PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_FQ_NAME)});
        HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                        element,
                                                                        description);
        QuickFixAction.registerQuickFixAction(highlightInfo, new MoveCatchUpFix(catchSection, section), null);
        QuickFixAction.registerQuickFixAction(highlightInfo, new DeleteCatchFix((PsiParameter)catchParameter), null);
        return highlightInfo;
      }
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkTernaryOperatorConditionIsBoolean(PsiExpression expression) {
    if (expression.getParent() instanceof PsiConditionalExpression
        && ((PsiConditionalExpression)expression.getParent()).getCondition() == expression
        && expression.getType() != null
        && !TypeConversionUtil.isBooleanType(expression.getType())) {
      PsiType foundType = expression.getType();
      Pair<String, String> typeInfo = createIncompatibleTypesDescriptionAndToolTip(PsiType.BOOLEAN, foundType);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, typeInfo.getFirst(), typeInfo.getSecond());
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkStatementPrependedWithCaseInsideSwitch(PsiStatement statement) {
    if (!(statement instanceof PsiSwitchLabelStatement)
        && statement.getParent() instanceof PsiCodeBlock
        && statement.getParent().getParent() instanceof PsiSwitchStatement
        && ((PsiCodeBlock)statement.getParent()).getStatements().length != 0
        && statement == ((PsiCodeBlock)statement.getParent()).getStatements()[0]) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, "Statement must be prepended with case label");
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkAssertOperatorTypes(PsiExpression expression) {
    if (!(expression.getParent() instanceof PsiAssertStatement)) {
      return null;
    }
    PsiAssertStatement assertStatement = (PsiAssertStatement)expression.getParent();
    PsiType type = expression.getType();
    if (type == null) return null;
    if (expression == assertStatement.getAssertCondition() && !TypeConversionUtil.isBooleanType(type)) {
      Pair<String, String> typeInfo = createIncompatibleTypesDescriptionAndToolTip(PsiType.BOOLEAN, type);
      // addTypeCast quickfix is not applicable here since no type can be cast to boolean
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               expression,
                                               typeInfo.getFirst(), typeInfo.getSecond());
    }
    else if (expression == assertStatement.getAssertDescription() && TypeConversionUtil.isVoidType(type)) {
      String description = "'void' type is not allowed here";
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               expression,
                                               description);
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkSynchronizedExpressionType(PsiExpression expression) {
    if (expression.getParent() instanceof PsiSynchronizedStatement) {
      PsiType type = expression.getType();
      if (type == null) return null;
      PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement)expression.getParent();
      if (expression == synchronizedStatement.getLockExpression() &&
          (type instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(type))) {
        PsiClassType objectType = PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope());
        Pair<String, String> typeInfo = createIncompatibleTypesDescriptionAndToolTip(objectType, type);
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 expression,
                                                 typeInfo.getFirst(), typeInfo.getSecond());
      }
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkConditionalExpressionBranchTypesMatch(PsiExpression expression) {
    if (!(expression.getParent() instanceof PsiConditionalExpression)) {
      return null;
    }
    PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression.getParent();
    // check else branches only
    if (conditionalExpression.getElseExpression() != expression) return null;
    final PsiExpression thenExpression = conditionalExpression.getThenExpression();
    assert thenExpression != null;
    PsiType thenType = thenExpression.getType();
    PsiType elseType = expression.getType();
    if (thenType == null || elseType == null) return null;
    if (conditionalExpression.getType() == null) {
      // cannot derive type of conditional expression
      Pair<String, String> typeInfo = createIncompatibleTypesDescriptionAndToolTip(thenType, elseType);
      // elsetype will never be castable to thentype, so no quick fix here
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               expression,
                                               typeInfo.getFirst(), typeInfo.getSecond());
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkSingleImportClassConflict(PsiImportStatement statement, Map<String, PsiClass> singleImportedClasses) {
    if (statement.isOnDemand()) return null;
    PsiElement element = statement.resolve();
    if (element instanceof PsiClass) {
      String name = ((PsiClass)element).getName();
      PsiClass importedClass = singleImportedClasses.get(name);
      if (importedClass != null && !element.getManager().areElementsEquivalent(importedClass, element)) {
        String description = MessageFormat.format("''{0}'' is already defined in a single-type import",
                                                  new Object[]{formatClass(importedClass)});
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 statement,
                                                 description);
      }
      singleImportedClasses.put(name, (PsiClass)element);
    }
    return null;
  }

  //@top
  static HighlightInfo checkConstantExpressionOverflow(PsiExpression expr) {
    boolean overflow = false;
    try {
      if (expr.getUserData(HAS_OVERFLOW_IN_CHILD) == null) {
        expr.getManager().getConstantEvaluationHelper().computeConstantExpression(expr, true);
      }
      else {
        overflow = true;
      }
    }
    catch (ConstantEvaluationOverflowException e) {
      overflow = true;
      return HighlightInfo.createHighlightInfo(HighlightInfoType.OVERFLOW_WARNING,
                                               expr,
                                               "Numeric overflow in expression");
    }
    finally {
      PsiElement parent = expr.getParent();
      if (parent instanceof PsiExpression && overflow) {
        parent.putUserData(HAS_OVERFLOW_IN_CHILD, "");
      }
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkAccessStaticMemberViaInstanceReference(PsiReferenceExpression expr, JavaResolveResult result) {
    PsiElement resolved = result.getElement();
    if (!DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile(expr).isToolEnabled(HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE)) {
      return null;
    }
    if (InspectionManagerEx.inspectionResultSuppressed(expr, HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE.getID())) return null;

    if (!(resolved instanceof PsiMember)) return null;
    PsiExpression qualifierExpression = expr.getQualifierExpression();
    if (qualifierExpression == null) return null;
    if (qualifierExpression instanceof PsiReferenceExpression
        && ((PsiReferenceExpression)qualifierExpression).resolve() instanceof PsiClass) {
      return null;
    }
    if (!((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) return null;

    String description = MessageFormat.format("Static member ''{0}.{1}'' accessed via instance reference",
                                              new Object[]{
                                                formatType(qualifierExpression.getType()),
                                                HighlightMessageUtil.getSymbolName(resolved, result.getSubstitutor())
                                              });

    HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ACCESS_STATIC_VIA_INSTANCE, expr, description);
    List<IntentionAction> options = new ArrayList<IntentionAction>();
    options.add(new SwitchOffToolAction(HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE));
    options.add(new AddNoInspectionCommentAction(HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE, expr));
    options.add(new AddNoInspectionDocTagAction(HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE, expr));
    options.add(new AddNoInspectionForClassAction(HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE, expr));
    options.add(new AddNoInspectionAllForClassAction(expr));
    options.add(new AddSuppressWarningsAnnotationAction(HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE, expr));
    options.add(new AddSuppressWarningsAnnotationForClassAction(HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE, expr));
    options.add(new AddSuppressWarningsAnnotationForAllAction(expr));
    QuickFixAction.registerQuickFixAction(highlightInfo, new AccessStaticViaInstanceFix(expr, result), options);
    return highlightInfo;
  }

  @SuppressWarnings({"NonConstantStringShouldBeStringBuffer"})
  public static Pair<String, String> createIncompatibleTypesDescriptionAndToolTip(PsiType lType, PsiType rType) {
    String toolTip = "<html><body>Incompatible types.";
    toolTip += "<table><tr>";
    PsiTypeParameter[] lTypeParams = PsiTypeParameter.EMPTY_ARRAY;
    PsiSubstitutor lTypeSubstitutor = PsiSubstitutor.EMPTY;
    if (lType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)lType).resolveGenerics();
      lTypeSubstitutor = resolveResult.getSubstitutor();
      PsiClass psiClass = resolveResult.getElement();
      if (psiClass instanceof PsiAnonymousClass) {
        lType = ((PsiAnonymousClass)psiClass).getBaseClassType();
        resolveResult = ((PsiClassType)lType).resolveGenerics();
        lTypeSubstitutor = resolveResult.getSubstitutor();
        psiClass = resolveResult.getElement();
      }
      lTypeParams = psiClass == null ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
    }
    PsiTypeParameter[] rTypeParams = PsiTypeParameter.EMPTY_ARRAY;
    PsiSubstitutor rTypeSubstitutor = PsiSubstitutor.EMPTY;
    if (rType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)rType).resolveGenerics();
      rTypeSubstitutor = resolveResult.getSubstitutor();
      PsiClass psiClass = resolveResult.getElement();
      if (psiClass instanceof PsiAnonymousClass) {
        rType = ((PsiAnonymousClass)psiClass).getBaseClassType();
        resolveResult = ((PsiClassType)rType).resolveGenerics();
        rTypeSubstitutor = resolveResult.getSubstitutor();
        psiClass = resolveResult.getElement();
      }
      rTypeParams = psiClass == null ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
    }

    String description = MessageFormat.format(INCOMPATIBLE_TYPES, new Object[]{formatType(lType), formatType(rType)});

    int typeParamColumns = Math.max(lTypeParams.length, rTypeParams.length);
    String requredRow = "";
    String foundRow = "";
    for (int i = 0; i < typeParamColumns; i++) {
      PsiTypeParameter lTypeParameter = i >= lTypeParams.length ? null : lTypeParams[i];
      PsiTypeParameter rTypeParameter = i >= rTypeParams.length ? null : rTypeParams[i];
      PsiType lSubstedType = lTypeParameter == null ? null : lTypeSubstitutor.substitute(lTypeParameter);
      PsiType rSubstedType = rTypeParameter == null ? null : rTypeSubstitutor.substitute(rTypeParameter);
      boolean matches = Comparing.equal(lSubstedType, rSubstedType);
      String openBrace = i == 0 ? "&lt;" : "";
      String closeBrace = i == typeParamColumns - 1 ? "&gt;" : ",";
      requredRow += "<td>" + (lTypeParams.length == 0 ? "" : openBrace) + redIfNotMatch(lSubstedType, matches) +
                    (i < lTypeParams.length ? closeBrace : "") +
                    "</td>";
      foundRow += "<td>" + (rTypeParams.length == 0 ? "" : openBrace) + redIfNotMatch(rSubstedType, matches) +
                  (i < rTypeParams.length ? closeBrace : "") +
                  "</td>";
    }
    PsiType lRawType = lType instanceof PsiClassType ? ((PsiClassType)lType).rawType() : lType;
    PsiType rRawType = rType instanceof PsiClassType ? ((PsiClassType)rType).rawType() : rType;
    boolean assignable = lRawType == null || rRawType == null ? true : TypeConversionUtil.isAssignable(lRawType, rRawType);
    toolTip += "<td>Required:</td>" +
               "<td>" + redIfNotMatch(TypeConversionUtil.erasure(lType), assignable) + "</td>" +
               requredRow;
    toolTip += "</tr><tr>";
    toolTip += "<td>Found:</td>" +
               "<td>" + redIfNotMatch(TypeConversionUtil.erasure(rType), assignable) + "</td>"
               + foundRow;

    toolTip += "</tr></table></body></html>";
    return Pair.create(description, toolTip);
  }

  private static String redIfNotMatch(PsiType type, boolean matches) {
    if (matches) return getFQName(type, false);
    return "<font color=red><b>" + getFQName(type, true) + "</b></font>";
  }

  private static String getFQName(PsiType type, boolean longName) {
    if (type == null) return "";
    return XmlUtil.escapeString(longName ? type.getInternalCanonicalText() : type.getPresentableText());
  }

  //@top
  @Nullable
  public static HighlightInfo checkMustBeThrowable(PsiType type, PsiElement context, boolean addCastIntention) {
    PsiElementFactory factory = context.getManager().getElementFactory();
    PsiClassType throwable = factory.createTypeByFQClassName("java.lang.Throwable", context.getResolveScope());
    if (throwable == null) return null;
    if (type == null) return null;
    if (!TypeConversionUtil.isAssignable(throwable, type)) {
      Pair<String, String> typeInfo = createIncompatibleTypesDescriptionAndToolTip(throwable, type);
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                      context,
                                                                      typeInfo.getFirst(), typeInfo.getSecond());
      if (addCastIntention && TypeConversionUtil.areTypesConvertible(type, throwable)) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new AddTypeCastFix(throwable, context), null);
      }
      return highlightInfo;
    }
    return null;
  }

  //@top
  @Nullable
  public static HighlightInfo checkMustBeThrowable(PsiClass aClass, PsiElement context, boolean addCastIntention) {
    if (aClass == null) return null;
    PsiClassType type = aClass.getManager().getElementFactory().createType(aClass);
    return checkMustBeThrowable(type, context, addCastIntention);
  }

  //@top
  @Nullable
  public static HighlightInfo checkLabelDefined(PsiIdentifier labelIdentifier, PsiStatement exitedStatement) {
    if (labelIdentifier == null) return null;
    String label = labelIdentifier.getText();
    if (label == null) return null;
    if (exitedStatement == null) {
      String message = MessageFormat.format("Undefined label: ''{0}''",
                                            new Object[]{label});
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               labelIdentifier,
                                               message);
    }
    return null;
  }

  public static TextRange getMethodDeclarationTextRange(PsiMethod method) {
    int start = method.getModifierList().getTextRange().getStartOffset();
    int end = method.getThrowsList().getTextRange().getEndOffset();
    return new TextRange(start, end);
  }

  //@top
  @Nullable
  public static HighlightInfo checkReference(PsiJavaCodeReferenceElement ref, JavaResolveResult result, PsiElement resolved) {
    PsiElement refName = ref.getReferenceNameElement();

    if (!(refName instanceof PsiIdentifier) && !(refName instanceof PsiKeyword)) return null;
    HighlightInfo highlightInfo = checkMemberReferencedBeforeConstructorCalled(ref);
    if (highlightInfo != null) return highlightInfo;

    PsiElement refParent = ref.getParent();
    if (!(refParent instanceof PsiMethodCallExpression)) {
      if (resolved == null) {
        // do not highlight unknown packages - javac does not care about illegal package names
        if (isInsidePackageStatement(refName)) return null;
        if (result.isPackagePrefixPackageReference()) return null;
        String description = MessageFormat.format(HighlightVisitorImpl.UNKNOWN_SYMBOL, new Object[]{refName.getText()});

        HighlightInfoType type = HighlightInfoType.WRONG_REF;
        List<IntentionAction> options = new ArrayList<IntentionAction>();
        if (PsiTreeUtil.getParentOfType(ref, PsiDocComment.class) != null) {
          if (!DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile(ref).isToolEnabled(HighlightDisplayKey.JAVADOC_ERROR)) {
            return null;
          }
          type = HighlightInfoType.JAVADOC_WRONG_REF;
          options.add(new SwitchOffToolAction(HighlightDisplayKey.JAVADOC_ERROR));
        }

        PsiElement parent = PsiTreeUtil.getParentOfType(ref, PsiNewExpression.class, PsiMethod.class);
        HighlightInfo info = HighlightInfo.createHighlightInfo(type, refName, description);
        QuickFixAction.registerQuickFixAction(info, new ImportClassAction(ref), options);
        QuickFixAction.registerQuickFixAction(info, SetupJDKFix.getInstnace(), options);
        if (ref instanceof PsiReferenceExpression) {
          TextRange fixRange = HighlightMethodUtil.getFixRange(ref);
          PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
          QuickFixAction.registerQuickFixAction(info, fixRange, new CreateConstantFieldFromUsageAction(refExpr), options );
          QuickFixAction.registerQuickFixAction(info, fixRange, new CreateFieldFromUsageAction(refExpr), options );
          QuickFixAction.registerQuickFixAction(info, new RenameWrongRefAction(refExpr), options);
          if (!ref.isQualified()) {
            QuickFixAction.registerQuickFixAction(info, fixRange, new BringVariableIntoScopeAction(refExpr), options );
            QuickFixAction.registerQuickFixAction(info, fixRange, new CreateLocalFromUsageAction(refExpr), options);
            QuickFixAction.registerQuickFixAction(info, fixRange, new CreateParameterFromUsageAction(refExpr), options );
          }
        }
        QuickFixAction.registerQuickFixAction(info, new CreateClassFromUsageAction(ref, true), options);
        QuickFixAction.registerQuickFixAction(info, new CreateClassFromUsageAction(ref, false), options);
        if (parent instanceof PsiNewExpression) {
          QuickFixAction.registerQuickFixAction(info, new CreateClassFromNewAction((PsiNewExpression)parent), options);
        }
        return info;
      }

      if (!result.isValidResult()) {
        if (!result.isAccessible()) {
          String description = buildProblemWithAccessDescription(resolved, ref, result);
          HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getReferenceNameElement(),
                                                                 description);
          if (result.isStaticsScopeCorrect()) {
            registerAccessQuickFixAction((PsiMember)resolved, ref, info);
            if (ref instanceof PsiReferenceExpression) {
              QuickFixAction.registerQuickFixAction(info, new RenameWrongRefAction((PsiReferenceExpression)ref), null);
            }
          }
          return info;
        }

        if (!result.isStaticsScopeCorrect()) {
          String description = buildProblemWithStaticDescription(resolved);
          HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getReferenceNameElement(),
                                                                 description);
          registerStaticProblemQuickFixAction(resolved, info, ref);
          if (ref instanceof PsiReferenceExpression) {
            QuickFixAction.registerQuickFixAction(info, new RenameWrongRefAction((PsiReferenceExpression)ref), null);
          }
          return info;
        }
      }
      if ((resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) && !(resolved instanceof ImplicitVariable)) {
        highlightInfo = HighlightControlFlowUtil.checkVariableMustBeFinal((PsiVariable)resolved, ref);
        if (highlightInfo != null) return highlightInfo;
      }
    }
    return highlightInfo;
  }

  private static boolean isInsidePackageStatement(PsiElement element) {
    while (element != null) {
      if (element instanceof PsiPackageStatement) return true;
      if (!(element instanceof PsiIdentifier) && !(element instanceof PsiJavaCodeReferenceElement)) return false;
      element = element.getParent();
    }
    return false;
  }

  @Nullable
  public static HighlightInfo checkReferenceList(PsiJavaCodeReferenceElement ref,
                                                 PsiReferenceList referenceList,
                                                 JavaResolveResult resolveResult) {
    PsiClass resolved = (PsiClass)resolveResult.getElement();
    PsiElement refGrandParent = referenceList.getParent();
    HighlightInfo highlightInfo = null;
    if (refGrandParent instanceof PsiClass) {
      if (refGrandParent instanceof PsiTypeParameter) {
        highlightInfo = GenericsHighlightUtil.checkTypeParameterExtendsList(referenceList, resolveResult, ref);
      }
      else {
        highlightInfo = HighlightClassUtil.checkExtendsClassAndImplementsInterface(referenceList, resolveResult, ref);
        if (highlightInfo == null) {
          highlightInfo = HighlightClassUtil.checkCannotInheritFromFinal(resolved, ref);
        }
        if (highlightInfo == null) {
          highlightInfo = GenericsHighlightUtil.checkCannotInheritFromEnum(resolved, ref);
        }
      }
    }
    else if (refGrandParent instanceof PsiMethod && ((PsiMethod)refGrandParent).getThrowsList() == referenceList) {
      highlightInfo = checkMustBeThrowable(resolved, ref, false);
    }
    return highlightInfo;
  }

  //@top
  @Nullable
  public static HighlightInfo checkDeprecated(PsiElement refElement,
                                              PsiElement elementToHighlight,
                                              DaemonCodeAnalyzerSettings settings) {
    if (!settings.getInspectionProfile(elementToHighlight).isToolEnabled(HighlightDisplayKey.DEPRECATED_SYMBOL)) return null;
    if (!(refElement instanceof PsiDocCommentOwner)) return null;
    if (!((PsiDocCommentOwner)refElement).isDeprecated()) return null;

    if (InspectionManagerEx.inspectionResultSuppressed(elementToHighlight, HighlightDisplayKey.DEPRECATED_SYMBOL.getID())) return null;

    String description = MessageFormat.format("''{0}'' is deprecated", new Object[]{
      HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY)});

    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.DEPRECATED_ATTRIBUTES);
    HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.DEPRECATED, elementToHighlight.getTextRange(), description,attributes);
    List<IntentionAction> options = new ArrayList<IntentionAction>();
    options.add(new SwitchOffToolAction(HighlightDisplayKey.DEPRECATED_SYMBOL));
    options.add(new AddNoInspectionCommentAction(HighlightDisplayKey.DEPRECATED_SYMBOL, elementToHighlight));
    options.add(new AddNoInspectionDocTagAction(HighlightDisplayKey.DEPRECATED_SYMBOL, elementToHighlight));
    options.add(new AddNoInspectionForClassAction(HighlightDisplayKey.DEPRECATED_SYMBOL, elementToHighlight));
    options.add(new AddNoInspectionAllForClassAction(elementToHighlight));
    options.add(new AddSuppressWarningsAnnotationAction(HighlightDisplayKey.DEPRECATED_SYMBOL, elementToHighlight));
    options.add(new AddSuppressWarningsAnnotationForClassAction(HighlightDisplayKey.DEPRECATED_SYMBOL, elementToHighlight));
    options.add(new AddSuppressWarningsAnnotationForAllAction(elementToHighlight));
    QuickFixAction.registerQuickFixAction(highlightInfo, new EmptyIntentionAction(HighlightDisplayKey.getDisplayNameByKey(HighlightDisplayKey.DEPRECATED_SYMBOL), options), options);
    return highlightInfo;
  }

  public static boolean isRootHighlighted(final PsiElement psiRoot) {
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(psiRoot.getProject());
    if(component == null) return true;

    final FileHighlighingSetting settingForRoot = component.getHighlightingSettingForRoot(psiRoot);
    return settingForRoot != FileHighlighingSetting.SKIP_HIGHLIGHTING;
  }

  public static void forceRootHighlighting(final PsiElement root, final boolean highlightFlag) {
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(root.getProject());
    if(component == null) return;
    final PsiFile file = root.getContainingFile();
    final FileHighlighingSetting highlightingLevel = highlightFlag
                                                     ? FileHighlighingSetting.FORCE_HIGHLIGHTING
                                                     : FileHighlighingSetting.SKIP_HIGHLIGHTING;
    if (file != null && file instanceof JspFile && root.getLanguage() instanceof JavaLanguage){
      //highlight both java roots
      final JspClass jspClass = ((JspClass)((JspFile)file).getJavaRoot());
      component.setHighlightingSettingForRoot(jspClass.getClassDummyHolder(), highlightingLevel);
      component.setHighlightingSettingForRoot(jspClass.getMethodDummyHolder(), highlightingLevel);
    } else {
      component.setHighlightingSettingForRoot(root, highlightingLevel);
    }
  }

  public static boolean isRootInspected(final PsiElement psiRoot) {
    if(!isRootHighlighted(psiRoot)) return false;
    final Project project = psiRoot.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile virtualFile = psiRoot.getContainingFile().getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return false;
    if (fileIndex.isInLibrarySource(virtualFile) || fileIndex.isInLibraryClasses(virtualFile)) return false;
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(project);
    if(component == null) return true;

    final FileHighlighingSetting settingForRoot = component.getHighlightingSettingForRoot(psiRoot);
    return settingForRoot != FileHighlighingSetting.SKIP_INSPECTION;
  }

  public static void forceRootInspection(final PsiElement root, final boolean inspectionFlag) {
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(root.getProject());
    if(component == null) return;
    final PsiFile file = root.getContainingFile();
    final FileHighlighingSetting inspectionLevel = inspectionFlag
                                                   ? FileHighlighingSetting.FORCE_HIGHLIGHTING
                                                   : FileHighlighingSetting.SKIP_INSPECTION;
    if (file != null && file instanceof JspFile && root.getLanguage() instanceof JavaLanguage){
      //highlight both java roots
      final JspClass jspClass = ((JspClass)((JspFile)file).getJavaRoot());
      component.setHighlightingSettingForRoot(jspClass.getClassDummyHolder(), inspectionLevel);
      component.setHighlightingSettingForRoot(jspClass.getMethodDummyHolder(), inspectionLevel);
    } else {
      component.setHighlightingSettingForRoot(root, inspectionLevel);
    }
  }

  public static HighlightInfo convertToHighlightInfo(Annotation annotation) {
    HighlightInfo info = new HighlightInfo(annotation.getTextAttributes(), convertType(annotation), annotation.getStartOffset(), annotation.getEndOffset(),
                                           annotation.getMessage(), annotation.getTooltip(), annotation.getSeverity(), annotation.isAfterEndOfLine(), annotation.needsUpdateOnTyping());
    List<Pair<IntentionAction, TextRange>> fixes = annotation.getQuickFixes();
    if (fixes != null) {
      for (Pair<IntentionAction, TextRange> pair : fixes) {
        QuickFixAction.registerQuickFixAction(info, pair.getSecond(), pair.getFirst(), null);
      }
    }
    return info;
  }

  private static HighlightInfoType convertType(Annotation annotation) {
    ProblemHighlightType type = annotation.getHighlightType();
    if (type == ProblemHighlightType.LIKE_UNUSED_SYMBOL) return HighlightInfoType.UNUSED_SYMBOL;
    if (type == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) return HighlightInfoType.WRONG_REF;
    if (type == ProblemHighlightType.LIKE_DEPRECATED) return HighlightInfoType.DEPRECATED;
    return annotation.getSeverity() == HighlightSeverity.ERROR ? HighlightInfoType.ERROR :
           annotation.getSeverity() == HighlightSeverity.WARNING ? HighlightInfoType.WARNING :
           HighlightInfoType.INFORMATION;
  }
}
