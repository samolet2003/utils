package ru.samolet2003.utils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang.StringUtils;

public class PropertyUtils {
    static {
        registerConverters();
    }

    private static void registerConverters() {
        Converter previuosConverter = ConvertUtils.lookup(Calendar.class);
        if (previuosConverter == null || !(previuosConverter instanceof DateToCalendarConverter))
            ConvertUtils.register(new DateToCalendarConverter(previuosConverter), Calendar.class);
        previuosConverter = ConvertUtils.lookup(Date.class);
        if (previuosConverter == null || !(previuosConverter instanceof CalendarToDateConverter))
            ConvertUtils.register(new CalendarToDateConverter(previuosConverter), Date.class);
    }

    public static void copySimpleProperties(Object dest, Object orig, String... excludeFields) {
        // Вызываю здесь регистрацию конвертеров, т.к. в ряде случаев
        // статической инициализации не хватает.
        // Например, если PropertyUtils используется в web модуле и в ejb и
        // политика загрузки классов в WAR модулях
        // имеет значение "MODULE" или "Mulpiple", т.е. загрузкичк классов
        // приложения и веб-модуля независимы.
        registerConverters();

        if (dest == null)
            throw new IllegalArgumentException("No destination bean specified");
        if (orig == null)
            throw new IllegalArgumentException("No origin bean specified");

        PropertyDescriptor destDescriptors[] = org.apache.commons.beanutils.PropertyUtils.getPropertyDescriptors(dest);
        for (int i = 0; i < destDescriptors.length; i++) {
            String name = destDescriptors[i].getName();
            boolean isSimpleOrigProperty = false;
            try {
                isSimpleOrigProperty = isSimpleProperty(org.apache.commons.beanutils.PropertyUtils
                        .getPropertyDescriptor(orig, name));
            } catch (Exception ex) {
            }
            if (isSimpleProperty(destDescriptors[i]) && isSimpleOrigProperty
                    && org.apache.commons.beanutils.PropertyUtils.isReadable(orig, name)
                    && org.apache.commons.beanutils.PropertyUtils.isWriteable(dest, name)) {
                if (isExcludeField(name, excludeFields))
                    continue;
                try {
                    Object value = org.apache.commons.beanutils.PropertyUtils.getSimpleProperty(orig, name);
                    if (value == null) { // Почему-то BeanUtils.copyProperty()
                                         // превращает null в 0 для типа Integer
                        org.apache.commons.beanutils.PropertyUtils.setSimpleProperty(dest, name, null);
                    } else {
                        // Если вернуть java.sql.Date в ответе AXIS то дата
                        // будет обернута не dateTime, а date и формат будет без
                        // времени,
                        // а это нам вредит при внешних вызовах
                        if (value instanceof java.sql.Date) {
                            value = new Date(((java.sql.Date) value).getTime());
                        }
                        BeanUtils.copyProperty(dest, name, value);
                    }
                } catch (Exception ex) {
                    throw new RuntimeException("Unable to set property '" + name + "'.", ex);
                }
            }
        }
    }

    private static boolean isExcludeField(String name, String... excludeFields) {
        boolean exclude = false;
        if (excludeFields != null) {
            for (String f : excludeFields) {
                if (StringUtils.equalsIgnoreCase(f, name)) {
                    exclude = true;
                    break;
                }
            }
        }
        return exclude;
    }

    public static boolean isSimpleProperty(PropertyDescriptor descriptor) {
        Class<?> propertyType = descriptor.getPropertyType();
        return propertyType != null
                && (Byte.TYPE.isAssignableFrom(propertyType) || Double.TYPE.isAssignableFrom(propertyType)
                        || Float.TYPE.isAssignableFrom(propertyType) || Integer.TYPE.isAssignableFrom(propertyType)
                        || Long.TYPE.isAssignableFrom(propertyType) || Short.TYPE.isAssignableFrom(propertyType)
                        || Character.TYPE.isAssignableFrom(propertyType) || Boolean.TYPE.isAssignableFrom(propertyType)
                        || Boolean.class.isAssignableFrom(propertyType) || Number.class.isAssignableFrom(propertyType)
                        || String.class.isAssignableFrom(propertyType) || Date.class.isAssignableFrom(propertyType)
                        || Character.class.isAssignableFrom(propertyType)
                        || Calendar.class.isAssignableFrom(propertyType) || byte[].class.isAssignableFrom(propertyType) || propertyType
                            .isEnum());
    }

    public static class DateToCalendarConverter implements Converter {
        private Converter previousConverter;

        public DateToCalendarConverter(Converter previousConverter) {
            this.previousConverter = previousConverter;
        }

        @SuppressWarnings("rawtypes")
        public Object convert(Class type, Object object) {
            if (object instanceof Date) {
                Calendar c = Calendar.getInstance();
                c.setTime((Date) object);
                return c;
            } else if (previousConverter != null)
                return previousConverter.convert(type, object);
            else if (object instanceof Calendar)
                return object;
            return null;
        }
    }

    public static class CalendarToDateConverter implements Converter {
        private Converter previousConverter;

        public CalendarToDateConverter(Converter previousConverter) {
            this.previousConverter = previousConverter;
        }

        @SuppressWarnings("rawtypes")
        public Object convert(Class type, Object object) {
            if (object instanceof Calendar)
                return ((Calendar) object).getTime();
            else if (previousConverter != null)
                return previousConverter.convert(type, object);
            else if (object instanceof Date)
                return object;
            return null;
        }
    }

    /**
     * 
     * @param instance
     * @param parameterIndex
     * @return
     */
    public static Class<?> getGenericParameter(Object instance, int parameterIndex) {
        if (instance == null)
            throw new NullPointerException("instance is null");
        Type type = instance.getClass().getGenericSuperclass();
        while (type != null && !(type instanceof ParameterizedType))
            type = ((Class<?>) type).getGenericSuperclass();
        if (type == null)
            throw new IllegalArgumentException("Can't find generic superclass");
        ParameterizedType parameterizedType = (ParameterizedType) type;
        if (parameterizedType.getActualTypeArguments().length <= parameterIndex)
            throw new IllegalArgumentException("Can't find argument " + parameterIndex + ". Class " + parameterizedType
                    + " has only " + parameterizedType.getActualTypeArguments().length + " arguments.");
        return (Class<?>) parameterizedType.getActualTypeArguments()[parameterIndex];
    }
}
