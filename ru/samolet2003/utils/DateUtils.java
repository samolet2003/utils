package ru.samolet2003.utils;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang3.StringUtils;

public class DateUtils {
    public static Calendar toCalendar(Date value) {
        if (value != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(value);
            return calendar;
        } else
            return null;
    }

    private static XMLGregorianCalendar resetTimezone(XMLGregorianCalendar value) {
        if (value == null)
            return null;
        XMLGregorianCalendar cal2 = (XMLGregorianCalendar) value.clone();
        // сбрасываем таймзону
        cal2.setTimezone(DatatypeConstants.FIELD_UNDEFINED);
        return cal2;
    }

    public static Calendar toCalendar(XMLGregorianCalendar value) {
        if (value == null)
            return null;
        return resetTimezone(value).toGregorianCalendar();
    }

    public static XMLGregorianCalendar toXmlCalendar(Date value) {
        return toXmlCalendar(toCalendar(value));
    }

    /**
     * Обрезать время
     * 
     * @param cal
     * @return
     */
    public static XMLGregorianCalendar toXmlCalendarTruncToDate(Calendar cal) {
        if (cal == null)
            return null;
        XMLGregorianCalendar xmlCalendar = toXmlCalendar(cal);
        xmlCalendar.setHour(DatatypeConstants.FIELD_UNDEFINED);
        xmlCalendar.setMinute(DatatypeConstants.FIELD_UNDEFINED);
        xmlCalendar.setSecond(DatatypeConstants.FIELD_UNDEFINED);
        xmlCalendar.setMillisecond(DatatypeConstants.FIELD_UNDEFINED);
        xmlCalendar.setFractionalSecond(null);
        return xmlCalendar;
    }

    public static XMLGregorianCalendar toXmlCalendarTruncToDate(Date date) {
        if (date == null)
            return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return toXmlCalendarTruncToDate(cal);
    }

    /**
     * Обрезать дату, оставить время
     * 
     * @param cal
     * @return
     */
    public static XMLGregorianCalendar toXmlCalendarTruncToTime(Calendar cal) {
        if (cal == null)
            return null;
        XMLGregorianCalendar xmlCalendar = toXmlCalendar(cal);
        xmlCalendar.setYear(DatatypeConstants.FIELD_UNDEFINED);
        xmlCalendar.setMonth(DatatypeConstants.FIELD_UNDEFINED);
        xmlCalendar.setDay(DatatypeConstants.FIELD_UNDEFINED);
        xmlCalendar.setMillisecond(DatatypeConstants.FIELD_UNDEFINED);
        xmlCalendar.setFractionalSecond(null);
        return xmlCalendar;
    }

    public static XMLGregorianCalendar toXmlCalendarTruncToTime(Date date) {
        if (date == null)
            return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return toXmlCalendarTruncToTime(cal);
    }

    public static XMLGregorianCalendar toXmlCalendar(Calendar cal) {
        if (cal == null)
            return null;
        assert cal instanceof GregorianCalendar;
        try {
            XMLGregorianCalendar xcal = DatatypeFactory.newInstance().newXMLGregorianCalendar((GregorianCalendar) cal);
            return resetTimezone(xcal);
        } catch (DatatypeConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Date toDate(Calendar value) {
        return value != null ? value.getTime() : null;
    }

    public static Date toDate(XMLGregorianCalendar value) {
        if (value == null)
            return null;
        // сбрасываем таймзону
        value.setTimezone(DatatypeConstants.FIELD_UNDEFINED);
        return value.toGregorianCalendar().getTime();
    }

    /**
     * Преобразовать дату в строку для логирования
     * 
     * @param date
     * @return
     */
    public static String toLogString(Date date) {
        if (date == null)
            return "";
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        return toLogString(c);
    }

    /**
     * Преобразовать дату в строку для логирования
     * 
     * @param c
     * @return
     */
    public static String toLogString(Calendar c) {
        String day = StringUtils.leftPad(String.valueOf(c.get(Calendar.DAY_OF_MONTH)), 2, '0');
        String month = StringUtils.leftPad(String.valueOf(c.get(Calendar.MONTH) + 1), 2, '0');
        String s = day + "." + month + "." + c.get(Calendar.YEAR);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        int second = c.get(Calendar.SECOND);
        if (hour != 0 || minute != 0 || second != 0)
            s += "_" + StringUtils.leftPad(String.valueOf(hour), 2, '0') + ":"
                    + StringUtils.leftPad(String.valueOf(minute), 2, '0') + ":"
                    + StringUtils.leftPad(String.valueOf(second), 2, '0');
        if (!TimeZone.getDefault().equals(c.getTimeZone())) {
            s += "_" + c.getTimeZone().getID();
        }
        return s;
    }
}
