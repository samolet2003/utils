package ru.samolet2003.utils;

import java.util.HashSet;
import java.util.Set;

import org.apache.axis.AxisFault;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;

public class ExceptionUtils {
    public static String getActualErrorMessage(Throwable ex) {
        if (ex == null)
            return "?null?";
        Set<Throwable> hs = new HashSet<Throwable>();
        Throwable e = ex;
        StringBuilder result = new StringBuilder();
        while (e != null) {
            // защита от зацикливания
            if (hs.contains(e))
                break;
            hs.add(e);
            String msg = e.getMessage();
            if (e instanceof AxisFault) {
                msg = ((AxisFault) e).getFaultString();
            } else if (e.getClass().getSimpleName().equals("UnknownLocalException")) {
                e = e.getCause();
                continue;
            }
            boolean skipClassname = /*BusinessException.class.equals(e.getClass())
                    ||*/ RuntimeException.class.equals(e.getClass())
                    || e.getClass().getName().contains("JMSHelperException")
                    || e.getClass().getName().equals("StopListBusinessException");
            if (!skipClassname || !StringUtils.isEmpty(msg)) {
                if (result.length() > 0)
                    result.append(", ");
                if (!skipClassname)
                    result.append(ClassUtils.getShortClassName(e, ""));
                if (!StringUtils.isEmpty(msg) && !skipClassname)
                    result.append(": ");
                if (!StringUtils.isEmpty(msg))
                    result.append(msg);
            }
            e = e.getCause();
        }
        if (result.length() == 0) {
            result.append(ClassUtils.getShortClassName(ex, ""));
        }
        return result.toString();
    }
}
