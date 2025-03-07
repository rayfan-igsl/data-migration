package com.igsl.migration.tools;

import com.twelvemonkeys.lang.StringUtil;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * @ Description:
 * @ Author: Seven
 * @ Date: 2022/6/21  10:40
 */
@Slf4j
public class GetDateUtils {
    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    private static SimpleDateFormat sdfs = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static List<String> getYearsBetweenString(String minDate, String maxDate) {
        ArrayList<String> result = new ArrayList<String>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy");//格式化为年月
        Calendar min = Calendar.getInstance();
        Calendar max = Calendar.getInstance();
        try {
            min.setTime(sdf.parse(minDate));
            max.setTime(sdf.parse(maxDate));
            max.add(Calendar.DATE, +1);
            Calendar curr = min;
            while (curr.before(max)) {
                result.add(sdf.format(curr.getTime()));
                curr.add(Calendar.YEAR, 1);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static Date getWorkDayEnd(List<String> holidayList, String today, int num)  {
        // 将字符串转换成日期
        Date date = null;
        try {
            date = sdfs.parse(today);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        // 获取工作日
        Date workDay = null;
        try {
            workDay = getWorkDay(holidayList, num, date, 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String workDayStr = sdf.format(workDay);
        long workTime = 0;    // 加1秒
        try {
            workTime = getTime(today, workDayStr) + 1000;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Date(workTime);
    }


    public static Date getWorkDay(List<String> holidayList, int num, Date day, int n) throws Exception {
        int delay = 1;
        while (delay <= num) {
            // 获取前一天或后一天日期
            Date endDay = getDate(day, n);
            String time = sdf.format(endDay);
            //当前日期+1即tomorrow,判断是否是节假日,同时要判断是否是周末,都不是则将scheduleActiveDate日期+1,直到循环num次即可
            if (!isWeekend(time) && !isHoliday(time, holidayList)) {
                delay++;
            } else if (isWeekend(time)) {
                System.out.println(time + "::是周末");
            } else if (isHoliday(time, holidayList)) {
                System.out.println(time + "::是节假日");
            }
            day = endDay;
        }
        return day;
    }

    public static long getTime(String start, String end) throws Exception {
        if (StringUtil.isEmpty(start) || StringUtil.isEmpty(end)) {
            throw new RuntimeException("today is empty");
        }

        long time1 = sdfs.parse(start).getTime();
        long time2 = sdf.parse(start).getTime();
        long time3 = sdf.parse(end).getTime();

        long time = time3 + (time1 - time2);

        return time;
    }

    public static Date getDate(Date date, int n) {
        if (n > 0) {    // 获取前一天
            date = getTomorrow(date);
        }
        if (n < 0) {    // 获取后一天
            date = getYesterday(date);
        }
        return date;
    }
    public static Date getTomorrow(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, +1);
        date = calendar.getTime();
        return date;
    }

    /**
     * 获取前一天的日期
     *
     * @param date
     * @return
     */
    public static Date getYesterday(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        date = calendar.getTime();
        return date;
    }

    /**
     * 判断是否是周末
     *
     * @param sdate
     * @return
     * @throws Exception
     */
    public static boolean isWeekend(String sdate) throws Exception {
        Date date = sdf.parse(sdate);
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * 判断是否是节假日
     *
     * @param sdate
     * @param list
     * @return
     * @throws Exception
     */
    public static boolean isHoliday(String sdate, List<String> list) throws Exception {
        if (list.size() > 0) {
            for (int i = 0; i < list.size(); i++) {
                if (sdate.equals(list.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String get65yearDate(Date todayDate){
        Calendar rightNow = Calendar.getInstance();
        rightNow.setTime(todayDate);
        rightNow.add(Calendar.YEAR,-65);//日期减65年
        Date dt1=rightNow.getTime();
        return sdf.format(dt1);
    }


    public static Date get65yearDates( Date todayDate){
        Calendar rightNow = Calendar.getInstance();
        rightNow.setTime(todayDate);
        rightNow.add(Calendar.YEAR,-65);//日期减65年
        Date dt1=rightNow.getTime();
        return dt1;
    }
    
  //获取指定日期的前几个月或者后几个月
    public static String getPrevMonthDate(Date date,int n) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MONTH, -n);
        return new SimpleDateFormat("MM-yyyy").format(calendar.getTime());
    }
    
    public static Date getDateBeforeOrAfter(Date date, int n) {
    	Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, n);
        return calendar.getTime();
    }
	
	public static String getDateTimeUTCString(LocalDateTime dt) {
		if (null == dt) {
			dt = LocalDateTime.now();
		}
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        return dt.format(formatter);
	}
}
