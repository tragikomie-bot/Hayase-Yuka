package cn.qinglan.ledger;
import java.time.*;
final class ScheduleDates {
    static LocalDate first(String frequency,int day,LocalDate start){
        if("weekly".equals(frequency)){if(day<1||day>7)throw new IllegalArgumentException("星期无效");return start.plusDays((day-start.getDayOfWeek().getValue()+7)%7);}
        if(!"monthly".equals(frequency)||day<1||day>31)throw new IllegalArgumentException("周期无效");
        LocalDate date=start.withDayOfMonth(Math.min(day,start.lengthOfMonth()));
        if(date.isBefore(start)){LocalDate next=start.plusMonths(1).withDayOfMonth(1);date=next.withDayOfMonth(Math.min(day,next.lengthOfMonth()));}return date;
    }
    static LocalDate after(String frequency,int day,LocalDate previous){return first(frequency,day,previous.plusDays(1));}
    static long dueMillis(String date,String time){return LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
}
