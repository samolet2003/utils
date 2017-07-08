package ru.samolet2003.utils;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JaxWSHelper {
    private static final Logger log = LoggerFactory.getLogger(JaxWSHelper.class);

    public static void copyProperties(Object dest, Object orig) {
        if (dest == null)
            throw new IllegalArgumentException("No destination bean specified");
        if (orig == null)
            throw new IllegalArgumentException("No origin bean specified");
        for (PropertyDescriptor destProp : getPropertyDescriptors(dest)) {
            String srcPropName = null;
            String destPropName = null;
            try {
                if (destProp.getReadMethod() != null && destProp.getReadMethod().isAnnotationPresent(SkipProp.class)) {
                    continue;
                }
                if (destProp.getName().equals("any")) {
                    continue;
                }
                destPropName = destProp.getName();
                if (destProp.getWriteMethod() == null) {
                    if (java.util.Collection.class.isAssignableFrom(destProp.getPropertyType())
                            && destProp.getReadMethod() != null
                            && destProp.getReadMethod().invoke(dest, new Object[0]) != null) {
                        // когда нет сеттера а свойство=коллекция
                        // инициализируемая в геттере
                        // log.warn("Found collection property without setter: "
                        // + destPropName + " class: "
                        // + dest.getClass());
                    } else {
                        log.trace("Not writeable property " + destPropName + " of " + dest.getClass());
                        continue;
                    }
                }
                srcPropName = findCorrespondingReadProperty(destPropName, orig, dest);
                log.trace("DestClass=" + dest.getClass().getName() + " DestProperty=" + destPropName + " SrcClass="
                        + orig.getClass().getName() + " SrcProperty=" + srcPropName);

                if (srcPropName == null) {
                    log.warn("Cannot find corresponding src property. DestClass=" + dest.getClass().getName()
                            + " DestProperty=" + destPropName + " SrcClass=" + orig.getClass().getName()
                            + " SrcProperty=???");
                    continue;
                }
                PropertyDescriptor origProp;
                origProp = getPropertyDescriptor(orig, srcPropName);
                if (origProp == null) {
                    log.warn("Cannot find property descriptor property=" + srcPropName + " obj=" + orig);
                    continue;
                } else if (origProp.getReadMethod() == null) {
                    log.warn("Not readable property " + srcPropName + " of " + orig.getClass());
                    continue;
                } else if (origProp.getReadMethod().isAnnotationPresent(SkipProp.class)) {
                    continue;
                }
                if (origProp.getPropertyType().equals(destProp.getPropertyType())) {
                    Object srcValue = origProp.getReadMethod().invoke(orig);
                    destProp.getWriteMethod().invoke(dest, srcValue);
                } else if (origProp.getPropertyType().isArray() && isListHolderProperty(destProp.getPropertyType())) {
                    arrayToListHolder(dest, orig, destProp, origProp);
                } else if (isListHolderProperty(origProp.getPropertyType()) && destProp.getPropertyType().isArray()) {
                    listHolderToArray(dest, orig, destProp, origProp);
                } else if (List.class.isAssignableFrom(origProp.getPropertyType())
                        && destProp.getPropertyType().isArray()) {
                    listToArray(dest, orig, destProp, origProp);
                } else if (origProp.getPropertyType().isArray()
                        && List.class.isAssignableFrom(destProp.getPropertyType())) {
                    arrayToList(dest, orig, destProp, origProp);
                } else if (origProp.getPropertyType().equals(java.util.Date.class)
                        && destProp.getPropertyType().equals(XMLGregorianCalendar.class)) {
                    // Date -> XMLGregorianCalendar
                    java.util.Date srcValue = (Date) origProp.getReadMethod().invoke(orig);
                    XMLGregorianCalendar destValue = null;
                    if (srcValue != null) {
                        destValue = DateUtils.toXmlCalendar(srcValue);
                    }
                    destProp.getWriteMethod().invoke(dest, destValue);
                } else if (origProp.getPropertyType().equals(XMLGregorianCalendar.class)
                        && destProp.getPropertyType().equals(java.util.Date.class)) {
                    // XMLGregorianCalendar -> Date
                    XMLGregorianCalendar srcValue = (XMLGregorianCalendar) origProp.getReadMethod().invoke(orig);
                    java.util.Date destValue = null;
                    if (srcValue != null) {
                        destValue = DateUtils.toDate(srcValue);
                    }
                    destProp.getWriteMethod().invoke(dest, destValue);
                } else if (origProp.getPropertyType().equals(java.util.Date.class)
                        && destProp.getPropertyType().equals(Calendar.class)) {
                    // Date -> Calendar
                    java.util.Date srcValue = (Date) origProp.getReadMethod().invoke(orig);
                    Calendar destValue = null;
                    if (srcValue != null) {
                        destValue = DateUtils.toCalendar(srcValue);
                    }
                    destProp.getWriteMethod().invoke(dest, destValue);
                } else if (origProp.getPropertyType().equals(Calendar.class)
                        && destProp.getPropertyType().equals(java.util.Date.class)) {
                    // Calendar -> Date
                    Calendar srcValue = (Calendar) origProp.getReadMethod().invoke(orig);
                    java.util.Date destValue = null;
                    if (srcValue != null) {
                        destValue = DateUtils.toDate(srcValue);
                    }
                    destProp.getWriteMethod().invoke(dest, destValue);
                } else if (origProp.getPropertyType().equals(XMLGregorianCalendar.class)
                        && destProp.getPropertyType().equals(java.util.Calendar.class)) {
                    // XMLGregorianCalendar -> Calendar
                    XMLGregorianCalendar srcValue = (XMLGregorianCalendar) origProp.getReadMethod().invoke(orig);
                    java.util.Calendar destValue = null;
                    if (srcValue != null) {
                        destValue = DateUtils.toCalendar(srcValue);
                    }
                    destProp.getWriteMethod().invoke(dest, destValue);
                } else if (origProp.getPropertyType().equals(java.util.Calendar.class)
                        && destProp.getPropertyType().equals(XMLGregorianCalendar.class)) {
                    // Calendar -> XMLGregorianCalendar
                    java.util.Calendar srcValue = (java.util.Calendar) origProp.getReadMethod().invoke(orig);
                    XMLGregorianCalendar destValue = null;
                    if (srcValue != null) {
                        destValue = DateUtils.toXmlCalendar(srcValue);
                    }
                    destProp.getWriteMethod().invoke(dest, destValue);
                } else if (origProp.getPropertyType().equals(Double.class)
                        && destProp.getPropertyType().equals(BigDecimal.class)) {
                    // Double -> BigDecimal
                    Object srcValue = origProp.getReadMethod().invoke(orig);
                    BigDecimal destValue = null;
                    if (srcValue != null)
                        destValue = BigDecimal.valueOf((Double) srcValue);
                    destProp.getWriteMethod().invoke(dest, destValue);
                } else if (origProp.getPropertyType().equals(BigDecimal.class)
                        && destProp.getPropertyType().equals(Double.class)) {
                    // BigDecimal -> Double
                    Object srcValue = origProp.getReadMethod().invoke(orig);
                    Double destValue = null;
                    if (srcValue != null)
                        destValue = ((BigDecimal) srcValue).doubleValue();
                    destProp.getWriteMethod().invoke(dest, destValue);
                } else {
                    Object srcValue = origProp.getReadMethod().invoke(orig);
                    Object destValue = null;
                    if (srcValue != null) {
                        if (origProp.getPropertyType().isPrimitive() || destProp.getPropertyType().isPrimitive()) {
                            // автобоксинг в/из примитивного типа
                            destValue = srcValue;
                        } else {
                            destValue = destProp.getPropertyType().newInstance();
                            copyProperties(destValue, srcValue);
                        }
                    }
                    if (srcValue == null && destProp.getPropertyType().isPrimitive()) {
                        // записывать null в примитивную property не будем
                        continue;
                    }
                    destProp.getWriteMethod().invoke(dest, destValue);
                }
            } catch (Exception e) {
                throw new RuntimeException("Cannot copy property: DestClass=" + dest.getClass().getName()
                        + " DestProperty=" + destPropName + " SrcClass=" + orig.getClass().getName() + " SrcProperty="
                        + srcPropName, e);
            }
        }
    }

    private static List<PropertyDescriptor> getPropertyDescriptors(Object obj) {
        PropertyDescriptor descriptors[] = org.apache.commons.beanutils.PropertyUtils.getPropertyDescriptors(obj);
        List<PropertyDescriptor> result = new ArrayList<PropertyDescriptor>();
        for (PropertyDescriptor pd : descriptors) {
            if (pd.getName().equals("class"))
                continue;
            result.add(pd);
        }
        return result;
    }

    private static Field getFieldByJavaBeanPropertyName(Class<?> clazz, String prop) {
        try {
            return clazz.getDeclaredField(prop);
        } catch (SecurityException ignore) {
            return null;
        } catch (NoSuchFieldException ignore) {
        }
        String prop0 = prop;
        prop = uncapitalize(prop0);
        if (!StringUtils.equals(prop, prop0)) {
            try {
                return clazz.getDeclaredField(prop);
            } catch (SecurityException ignore) {
            } catch (NoSuchFieldException ignore) {
            }
        }
        prop = capitalize(prop0);
        if (!StringUtils.equals(prop, prop0)) {
            try {
                return clazz.getDeclaredField(prop);
            } catch (SecurityException ignore) {
            } catch (NoSuchFieldException ignore) {
            }
        }
        return null;
    }

    private static PropertyDescriptor getPropertyDescriptor(Object obj, String propName) throws IntrospectionException,
            IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        PropertyDescriptor pd2 = PropertyUtils.getPropertyDescriptor(obj, propName);
        if (pd2 != null) {
            if (pd2.getReadMethod() == null) {
                if (pd2.getPropertyType().equals(Boolean.class)) {
                    // isSmthn(Boolean) -> PropertyUtils не может найти
                    // readMethod (т.к. он должен быть такой: getSmthn(Boolean))
                    Method[] methods = obj.getClass().getMethods();
                    Method getter = null;
                    for (Method m : methods) {
                        if (pd2.getPropertyType().equals(m.getReturnType())) {
                            if (m.getName().equalsIgnoreCase("is" + propName)) {
                                if (m.getParameterTypes().length == 0) {
                                    getter = m;
                                    break;
                                }
                            }
                        }
                    }
                    if (getter != null) {
                        pd2.setReadMethod(getter);
                        return pd2;
                    }
                }
            } else {
                return pd2;
            }
        }
        return null;
    }

    private static String getJavaBeanPropertyByFieldName(String fieldName, Object obj) {
        try {
            PropertyDescriptor pd2 = PropertyUtils.getPropertyDescriptor(obj, fieldName);
            if (pd2 != null) {
                if (pd2.getReadMethod() != null) {
                    return fieldName;
                } else if (pd2.getPropertyType().equals(Boolean.class)) {
                    // isSmthn(Boolean) -> PropertyUtils не может найти
                    // readMethod (т.к. он должен быть такой: getSmthn(Boolean))
                    return fieldName;
                }
            }
        } catch (IllegalAccessException ignore) {
        } catch (InvocationTargetException ignore) {
        } catch (NoSuchMethodException ignore) {
        }
        // если название поля не совпадает с названием в getter
        String fieldName0 = fieldName;
        fieldName = capitalize(fieldName0);
        if (!StringUtils.equals(fieldName0, fieldName)) {
            try {
                PropertyDescriptor pd2 = PropertyUtils.getPropertyDescriptor(obj, fieldName);
                if (pd2 != null && pd2.getReadMethod() != null)
                    return fieldName;
            } catch (IllegalAccessException ignore) {
            } catch (InvocationTargetException ignore) {
            } catch (NoSuchMethodException ignore) {
            }
        }
        fieldName = uncapitalize(fieldName0);
        if (!StringUtils.equals(fieldName0, fieldName)) {
            try {
                PropertyDescriptor pd2 = PropertyUtils.getPropertyDescriptor(obj, fieldName);
                if (pd2 != null && pd2.getReadMethod() != null)
                    return fieldName;
            } catch (IllegalAccessException ignore) {
            } catch (InvocationTargetException ignore) {
            } catch (NoSuchMethodException ignore) {
            }
        }
        return null;
    }

    private static String findCorrespondingReadProperty(String writeProp, Object readObj, Object writeObj) {
        String prop = getJavaBeanPropertyByFieldName(writeProp, readObj);
        if (prop != null)
            return prop;

        for (Field field : readObj.getClass().getDeclaredFields()) {
            XmlElement xmlElelementAannotation = field.getAnnotation(XmlElement.class);
            if (xmlElelementAannotation != null) {
                if (StringUtils.equals(writeProp, xmlElelementAannotation.name())) {
                    prop = getJavaBeanPropertyByFieldName(field.getName(), readObj);
                    if (prop != null)
                        return prop;
                }
            }
        }
        Field writeField = getFieldByJavaBeanPropertyName(writeObj.getClass(), writeProp);
        if (writeField == null) {
            // throw new
            // IllegalStateException("Cannot find corresponding class field for bean property "
            // + writeProp
            // + ". Class=" + writeObj.getClass());
            return null;
        }
        XmlElement writeXmlElelementAannotation = writeField.getAnnotation(XmlElement.class);
        if (writeXmlElelementAannotation != null) {
            String xmlElementName = writeXmlElelementAannotation.name();
            if (!StringUtils.equals("##default", xmlElementName)) {
                prop = getJavaBeanPropertyByFieldName(xmlElementName, readObj);
                if (prop != null)
                    return prop;
            }
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void arrayToListHolder(Object dest, Object orig, PropertyDescriptor destProp,
            PropertyDescriptor origProp) throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, InstantiationException, IntrospectionException {
        Object[] value = (Object[]) origProp.getReadMethod().invoke(orig);
        Object listHolderInstance = destProp.getPropertyType().newInstance();
        destProp.getWriteMethod().invoke(dest, listHolderInstance);
        Field listHolderField = getListHolderPropertyField(destProp.getPropertyType());
        String listHolderFieldName = listHolderField.getName();
        PropertyDescriptor d = getPropertyDescriptor(listHolderInstance, listHolderFieldName);
        if (d == null) {
            d = getPropertyDescriptor(listHolderInstance, capitalize(listHolderFieldName));
            if (d == null)
                throw new IllegalStateException("Cannot find list holder property " + listHolderFieldName);
        }
        List list = (List) d.getReadMethod().invoke(listHolderInstance);
        if (value != null) {
            for (Object arrObj : value) {
                Object listObj = null;
                if (arrObj != null) {
                    Type listHolderElementType = getListHolderElementType(listHolderField);
                    if (arrObj.getClass().equals(listHolderElementType)) {
                        listObj = arrObj;
                    } else {
                        listObj = ((Class<?>) listHolderElementType).newInstance();
                        copyProperties(listObj, arrObj);
                    }
                }
                list.add(listObj);
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void arrayToList(Object dest, Object orig, PropertyDescriptor destProp, PropertyDescriptor origProp)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
        Object[] value = (Object[]) origProp.getReadMethod().invoke(orig);
        List destList = (List) destProp.getReadMethod().invoke(dest, new Object[0]);
        if (destList == null) {
            destList = (List) destProp.getPropertyType().newInstance();
            destProp.getWriteMethod().invoke(dest, destList);
        } else {
            destList.clear();
        }
        Field destField = getFieldByJavaBeanPropertyName(dest.getClass(), destProp.getName());
        if (destField == null) {
            log.warn("Cannot find corresponding class field for bean property " + destProp.getName() + ". Class="
                    + dest.getClass() + ". -> Cannot copy property " + origProp + " of " + orig.getClass().getName()
                    + " to " + destProp + " of " + dest.getClass().getName());
            return;
        }
        Type listElementType = getListHolderElementType(destField);
        if (value != null) {
            for (Object arrObj : value) {
                Object listObj = null;
                if (arrObj != null) {
                    if (arrObj.getClass().equals(listElementType)) {
                        listObj = arrObj;
                    } else {
                        listObj = ((Class<?>) listElementType).newInstance();
                        copyProperties(listObj, arrObj);
                    }
                }
                destList.add(listObj);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void listHolderToArray(Object dest, Object orig, PropertyDescriptor destProp,
            PropertyDescriptor origProp) throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, InstantiationException, IntrospectionException {
        Class<?> arrComponentType = destProp.getPropertyType().getComponentType();
        Object listHolderInstance = origProp.getReadMethod().invoke(orig);
        if (listHolderInstance == null) {
            destProp.getWriteMethod().invoke(dest, new Object[] { Array.newInstance(arrComponentType, 0) });
            return;
        }
        Field listHolderPropertyField = getListHolderPropertyField(listHolderInstance.getClass());
        String listHolderFieldName = listHolderPropertyField.getName();
        PropertyDescriptor d = getPropertyDescriptor(listHolderInstance, listHolderFieldName);
        if (d == null) {
            d = getPropertyDescriptor(listHolderInstance, capitalize(listHolderFieldName));
            if (d == null)
                throw new IllegalStateException("Cannot find list holder property " + listHolderFieldName);
        }
        List list = (List) d.getReadMethod().invoke(listHolderInstance);
        Object[] arr = (Object[]) Array.newInstance(arrComponentType, list.size());
        destProp.getWriteMethod().invoke(dest, new Object[] { arr });
        int i = 0;
        for (Object listObj : list) {
            Object arrObj = null;
            if (listObj != null) {
                if (listObj.getClass().equals(arrComponentType)) {
                    arrObj = listObj;
                } else {
                    arrObj = arrComponentType.newInstance();
                    copyProperties(arrObj, listObj);
                }
            }
            arr[i++] = arrObj;
        }
    }

    private static void listToArray(Object dest, Object orig, PropertyDescriptor destProp, PropertyDescriptor origProp)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
        List<?> list = (List<?>) origProp.getReadMethod().invoke(orig);
        Class<?> arrComponentType = destProp.getPropertyType().getComponentType();
        Object[] arr = (Object[]) Array.newInstance(arrComponentType, list.size());
        destProp.getWriteMethod().invoke(dest, new Object[] { arr });
        int i = 0;
        for (Object listObj : list) {
            Object arrObj = null;
            if (listObj != null) {
                if (listObj.getClass().equals(arrComponentType)) {
                    arrObj = listObj;
                } else {
                    arrObj = arrComponentType.newInstance();
                    copyProperties(arrObj, listObj);
                }
            }
            arr[i++] = arrObj;
        }
    }

    private static boolean isListHolderProperty(Class<?> propertyType) {
        Field[] fields = propertyType.getDeclaredFields();
        if (fields.length != 1)
            return false;
        Field field = fields[0];
        if (!List.class.isAssignableFrom(field.getType()))
            return false;
        return true;
    }

    private static Field getListHolderPropertyField(Class<?> propertyType) {
        if (!isListHolderProperty(propertyType))
            throw new IllegalStateException("not a list holder: " + propertyType);
        Field[] fields = propertyType.getDeclaredFields();
        Field field = fields[0];
        return field;
    }

    private static Type getListHolderElementType(Field field) {
        Type genericType = field.getGenericType();
        Type[] actualTypeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
        return actualTypeArguments[0];
    }

    private static String capitalize(String fieldName) {
        // abcdOperatorStatus --> ABCDOperatorStatus
        StringBuilder ucName = new StringBuilder();
        boolean copy = false;
        for (int i = 0; i < fieldName.length(); i++) {
            char charAt = fieldName.charAt(i);
            if (!copy) {
                if (Character.isUpperCase(charAt)) {
                    copy = true;
                } else {
                    charAt = Character.toUpperCase(charAt);
                }
            }
            ucName.append(charAt);
        }
        return ucName.toString();
    }

    private static String uncapitalize(String fieldName) {
        // ABCDOperatorStatus --> abcdOperatorStatus
        // ABCD -> ABCD
        // Abcd -> abcd
        int capitalized = 0;
        for (int i = 0; i < fieldName.length(); i++) {
            char charAt = fieldName.charAt(i);
            if (Character.isUpperCase(charAt)) {
                capitalized++;
            } else {
                break;
            }
        }
        if (capitalized == 0 || capitalized == fieldName.length())
            return fieldName;
        if (capitalized > 1)
            capitalized--;
        StringBuilder lcName = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char charAt = fieldName.charAt(i);
            if (i < capitalized)
                charAt = Character.toLowerCase(charAt);
            lcName.append(charAt);
        }
        return lcName.toString();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD, ElementType.FIELD })
    public static @interface SkipProp {
    }
}
