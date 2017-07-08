package ru.samolet2003.utils;

import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Идея в том что toString вызывается по необходимости, что полезно при
 * логировании <br>
 * log.debug("блаблабла={}", ToStringHelper.create(obj));<br>
 * если уровень логирования DEBUG не включен то toString не будет вызван
 */
public class ToStringHelper {
    private static final Logger log = LoggerFactory.getLogger(ToStringHelper.class);
    private static final Class<?>[] wrappedClasses = new Class<?>[] { Collection.class, Date.class,
            XMLGregorianCalendar.class, Calendar.class, String.class, Map.class, Number.class, Enum.class };
    private static final Class<?>[] toStringClasses = new Class<?>[] { Number.class, Enum.class };
    private static final String[] toStringClasses2 = new String[] { "ObjectId", "ObjectIdData" };
    private final Object object;
    private final boolean multiline;
    // макс. глубина просмотра дерева объектов
    private int maxDepth = 10;
    // макс. глубина объекта при которой он выводится в многострочном режиме
    // если глубина больше то вывод происходит в однострочном режиме
    private int maxMultilineDepth = 2;
    private String excludeFields[];

    public ToStringHelper(Object object, boolean multiline) {
        this.object = object;
        this.multiline = multiline;
    }

    public ToStringHelper setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }

    public ToStringHelper setExcludeFields(String... excludeFields) {
        this.excludeFields = excludeFields;
        return this;
    }

    public ToStringHelper setMaxMultilineDepth(int maxMultilineDepth) {
        this.maxMultilineDepth = maxMultilineDepth;
        return this;
    }

    @Override
    public String toString() {
        if (object == null)
            return "null";
        try {
            Object obj = object;
            boolean wrap = false;
            for (Class<?> c : wrappedClasses) {
                if (c.isAssignableFrom(obj.getClass())) {
                    obj = new Wrapper(obj);
                    wrap = true;
                    break;
                }
            }
            ToStringStyle style;
            if (multiline) {
                style = new CustomMultilineRecursiveToStringStyle(maxDepth, maxMultilineDepth, 0, excludeFields);
                if (wrap) {
                    ((CustomMultilineRecursiveToStringStyle) style).spaces = 0;
                    ((CustomMultilineRecursiveToStringStyle) style).resetIndent();
                }
            } else {
                style = new CustomRecursiveToStringStyle(true, maxDepth, 0, excludeFields);
            }
            CustomReflectionToStringBuilder builder = new CustomReflectionToStringBuilder(obj, style);
            builder.setExcludeFieldNames(excludeFields);
            String s = builder.toString();
            if (wrap) {
                s = s.replaceAll("^ToStringHelper\\.Wrapper\\[\\s*obj=", "");
                s = s.replaceAll("]$", "");
            }
            s = s.replaceAll("(\\r?\\n)+$", "");
            return s;
        } catch (Exception e) {
            log.error("Ошибка в ToStringHelper obj={}", object, e);
            return object.toString();
        }
    }

    public static ToStringHelper multiline(Object o) {
        return new ToStringHelper(o, true);
    }

    public static ToStringHelper multiline(Object o, String... excludeFields) {
        ToStringHelper result = new ToStringHelper(o, true);
        result.excludeFields = excludeFields;
        return result;
    }

    public static ToStringHelper combined(Object o, int maxMultilineDepth) {
        ToStringHelper result = new ToStringHelper(o, true);
        result.maxMultilineDepth = maxMultilineDepth;
        return result;
    }

    public static ToStringHelper oneline(Object o) {
        return new ToStringHelper(o, false);
    }

    private static class CustomReflectionToStringBuilder extends ReflectionToStringBuilder {
        private CustomReflectionToStringBuilder(Object object, ToStringStyle style) {
            super(object, style);
        }

        @Override
        protected boolean accept(Field field) {
            boolean accept = super.accept(field);
            if (accept) {
                String name = field.getName();
                if ("__equalsCalc".equals(name))
                    return false;
                if ("__hashCodeCalc".equals(name))
                    return false;
                try {
                    // не показывать поля со значением null
                    Object value = field.get(getObject());
                    if (value == null)
                        return false;
                } catch (IllegalArgumentException ignore) {
                } catch (IllegalAccessException ignore) {
                }
            }
            return accept;
        }

        @Override
        protected Object getValue(Field field) throws IllegalArgumentException, IllegalAccessException {
            Object value = super.getValue(field);
            if (value instanceof Calendar) {
                Calendar c = (Calendar) value;
                return calendarToString(c);
            } else if (value instanceof XMLGregorianCalendar) {
                XMLGregorianCalendar c = (XMLGregorianCalendar) value;
                return c.toXMLFormat();
                // Calendar cal = ConvertUtils.toCalendar(c);
                // return calendarToString(cal);
            } else if (value instanceof String) {
                String s = (String) value;
                if (s.isEmpty())
                    return "\"\"";
            } else if (value instanceof Date) {
                Calendar c = Calendar.getInstance();
                c.setTime((Date) value);
                return calendarToString(c);
            } else if (value instanceof Throwable) {
                return ExceptionUtils.getActualErrorMessage((Throwable) value);
            }
            if (isToStringObj(value))
                return value.toString();
            return value;
        }

        private String calendarToString(Calendar c) {
            return DateUtils.toLogString(c);
        }

        @Override
        public String toString() {
            return super.toString();
        }
    }

    private static class CustomMultilineRecursiveToStringStyle extends RecursiveToStringStyle {
        private static final long serialVersionUID = 1L;

        /** Indenting of inner lines. */
        private int indent = 2;

        /** Current indenting. */
        private int spaces = 2;
        private final int maxDepth;
        private final int maxMultilineDepth;
        private final int depth;
        private final String[] excludeFieldNames;

        /**
         * Constructor.
         */
        public CustomMultilineRecursiveToStringStyle(int maxDepth, int maxMultilineDepth, int depth,
                String[] excludeFieldNames) {
            setUseClassName(true);
            setUseIdentityHashCode(false);
            setUseShortClassName(true);
            resetIndent();
            this.maxDepth = maxDepth;
            this.maxMultilineDepth = maxMultilineDepth;
            this.depth = depth;
            this.excludeFieldNames = excludeFieldNames;
        }

        /**
         * Resets the fields responsible for the line breaks and indenting. Must
         * be invoked after changing the {@link #spaces} value.
         */
        private void resetIndent() {
            setArrayStart("{" + SystemUtils.LINE_SEPARATOR + spacer(spaces));
            setArraySeparator("," + SystemUtils.LINE_SEPARATOR + spacer(spaces));
            setArrayEnd(SystemUtils.LINE_SEPARATOR + spacer(spaces - indent) + "}");

            setContentStart("[" + SystemUtils.LINE_SEPARATOR + spacer(spaces));
            setFieldSeparator("," + SystemUtils.LINE_SEPARATOR + spacer(spaces));
            setContentEnd(SystemUtils.LINE_SEPARATOR + spacer(spaces - indent) + "]");
        }

        /**
         * Creates a StringBuilder responsible for the indenting.
         * 
         * @param spaces
         *            how far to indent
         * @return a StringBuilder with {spaces} leading space characters.
         */
        private StringBuilder spacer(int spaces) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < spaces; i++) {
                sb.append(" ");
            }
            return sb;
        }

        @Override
        public void appendDetail(StringBuffer buffer, String fieldName, Object value) {
            spaces += indent;
            resetIndent();
            if (!ClassUtils.isPrimitiveWrapper(value.getClass()) && !String.class.equals(value.getClass())
                    && accept(value.getClass())) {
                if (isToStringObj(value)) {
                    buffer.append(value.toString());
                } else {
                    if (depth + 1 < maxDepth) {
                        ToStringStyle style;
                        if (depth >= maxMultilineDepth) {
                            style = new CustomRecursiveToStringStyle(false, maxDepth, depth + 1, excludeFieldNames);
                        } else {
                            style = new CustomMultilineRecursiveToStringStyle(maxDepth, maxMultilineDepth, depth + 1,
                                    excludeFieldNames);
                            ((CustomMultilineRecursiveToStringStyle) style).spaces = spaces;
                            ((CustomMultilineRecursiveToStringStyle) style).resetIndent();
                        }
                        CustomReflectionToStringBuilder builder = new CustomReflectionToStringBuilder(value, style);
                        builder.setExcludeFieldNames(excludeFieldNames);
                        buffer.append(builder.toString());
                    } else {
                        appendClassName(buffer, value);
                        buffer.append('@');
                        buffer.append(Integer.toHexString(System.identityHashCode(value)));
                    }
                }
            } else {
                super.appendDetail(buffer, fieldName, value);
            }
            spaces -= indent;
            resetIndent();
        }

        @Override
        protected void appendDetail(final StringBuffer buffer, final String fieldName, final Object[] array) {
            if (array != null && array.length == 0) {
                buffer.append("{}");
                return;
            }
            spaces += indent;
            resetIndent();
            super.appendDetail(buffer, fieldName, array);
            spaces -= indent;
            resetIndent();
        }

        @Override
        protected void reflectionAppendArrayDetail(final StringBuffer buffer, final String fieldName, final Object array) {
            spaces += indent;
            resetIndent();
            super.reflectionAppendArrayDetail(buffer, fieldName, array);
            spaces -= indent;
            resetIndent();
        }

        @Override
        protected void appendDetail(final StringBuffer buffer, final String fieldName, final long[] array) {
            if (array != null && array.length == 0) {
                buffer.append("{}");
                return;
            }
            spaces += indent;
            resetIndent();
            super.appendDetail(buffer, fieldName, array);
            spaces -= indent;
            resetIndent();
        }

        @Override
        protected void appendDetail(final StringBuffer buffer, final String fieldName, final int[] array) {
            if (array != null && array.length == 0) {
                buffer.append("{}");
                return;
            }
            spaces += indent;
            resetIndent();
            super.appendDetail(buffer, fieldName, array);
            spaces -= indent;
            resetIndent();
        }

        @Override
        protected void appendDetail(final StringBuffer buffer, final String fieldName, final short[] array) {
            if (array != null && array.length == 0) {
                buffer.append("{}");
                return;
            }
            spaces += indent;
            resetIndent();
            super.appendDetail(buffer, fieldName, array);
            spaces -= indent;
            resetIndent();
        }

        @Override
        protected void appendDetail(final StringBuffer buffer, final String fieldName, final byte[] array) {
            if (array != null && array.length == 0) {
                buffer.append("{}");
                return;
            }
            spaces += indent;
            resetIndent();
            super.appendDetail(buffer, fieldName, array);
            spaces -= indent;
            resetIndent();
        }

        @Override
        protected void appendDetail(final StringBuffer buffer, final String fieldName, final char[] array) {
            if (array != null && array.length == 0) {
                buffer.append("{}");
                return;
            }
            spaces += indent;
            resetIndent();
            super.appendDetail(buffer, fieldName, array);
            spaces -= indent;
            resetIndent();
        }

        @Override
        protected void appendDetail(final StringBuffer buffer, final String fieldName, final double[] array) {
            if (array != null && array.length == 0) {
                buffer.append("{}");
                return;
            }
            spaces += indent;
            resetIndent();
            super.appendDetail(buffer, fieldName, array);
            spaces -= indent;
            resetIndent();
        }

        @Override
        protected void appendDetail(final StringBuffer buffer, final String fieldName, final float[] array) {
            if (array != null && array.length == 0) {
                buffer.append("{}");
                return;
            }
            spaces += indent;
            resetIndent();
            super.appendDetail(buffer, fieldName, array);
            spaces -= indent;
            resetIndent();
        }

        @Override
        protected void appendDetail(final StringBuffer buffer, final String fieldName, final boolean[] array) {
            if (array != null && array.length == 0) {
                buffer.append("{}");
                return;
            }
            spaces += indent;
            resetIndent();
            super.appendDetail(buffer, fieldName, array);
            spaces -= indent;
            resetIndent();
        }

        @Override
        protected void appendCyclicObject(StringBuffer buffer, String fieldName, Object value) {
            buffer.append("CYCLIC_OBJECT_");
            super.appendCyclicObject(buffer, fieldName, value);
        }
    }

    private static class CustomRecursiveToStringStyle extends ToStringStyle {
        private static final long serialVersionUID = 1L;
        private final int maxDepth;
        private final int depth;
        private final String[] excludeFieldNames;

        public CustomRecursiveToStringStyle(boolean useClassName, int maxDepth, int depth, String[] excludeFieldNames) {
            setUseClassName(useClassName);
            setUseIdentityHashCode(false);
            setUseShortClassName(true);
            this.maxDepth = maxDepth;
            this.depth = depth;
            this.excludeFieldNames = excludeFieldNames;
        }

        @Override
        public void appendDetail(final StringBuffer buffer, final String fieldName, final Object value) {
            if (!ClassUtils.isPrimitiveWrapper(value.getClass()) && !String.class.equals(value.getClass())
                    && accept(value.getClass())) {
                if (isToStringObj(value)) {
                    buffer.append(value.toString());
                } else {
                    if (depth + 1 < maxDepth) {
                        CustomRecursiveToStringStyle style = new CustomRecursiveToStringStyle(isUseClassName(),
                                maxDepth, depth + 1, excludeFieldNames);
                        CustomReflectionToStringBuilder builder = new CustomReflectionToStringBuilder(value, style);
                        builder.setExcludeFieldNames(excludeFieldNames);
                        buffer.append(builder.toString());
                    } else {
                        appendClassName(buffer, value);
                        buffer.append('@');
                        buffer.append(Integer.toHexString(System.identityHashCode(value)));
                    }
                }
            } else {
                super.appendDetail(buffer, fieldName, value);
            }
        }

        @Override
        protected void appendDetail(final StringBuffer buffer, final String fieldName, final Collection<?> coll) {
            appendClassName(buffer, coll);
            appendIdentityHashCode(buffer, coll);
            appendDetail(buffer, fieldName, coll.toArray());
        }

        protected boolean accept(final Class<?> clazz) {
            return true;
        }

        // @Override
        // protected String getShortClassName(Class<?> cls) {
        // return super.getShortClassName(cls);
        // }
    }

    private static class Wrapper {
        @SuppressWarnings("unused")
        private final Object obj;

        private Wrapper(Object obj) {
            this.obj = obj;
        }
    }

    private static boolean isToStringObj(Object obj) {
        if (obj == null)
            return false;
        for (Class<?> c : toStringClasses) {
            if (c.isInstance(obj)) {
                return true;
            }
        }
        String objClassName = obj.getClass().getSimpleName();
        for (String s : toStringClasses2) {
            if (StringUtils.equals(objClassName, s))
                return true;
        }
        return false;
    }
}