package com.marvin.daka.calendar

/**
 * 手机上的一个日历账户。
 *
 * 一台手机上通常有好几个：谷歌日历（按账号一个）、「本地日历」、
 * 厂商自带的（小米日历、华为日历）、Exchange 等。
 * 用户要自己选同步到哪一个——不同日历的同步行为差别很大。
 *
 * @property id          系统日历 id，写入事件时要填
 * @property displayName 日历名称，给用户看
 * @property accountName 账号名。本地日历通常是设备名或 "本地"
 * @property color       这个日历在日历 App 里显示的颜色，UI 里当小圆点用
 */
data class CalendarAccount(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
    val isPrimary: Boolean
)

/**
 * 系统日历里的一个日程实例。
 *
 * 注意是「实例」而不是「事件」：重复事件（比如每周例会）在日历里是一条记录，
 * 但查 Instances 表时，系统会自动把它展开成**每周各一条**返回给我们。
 * 这正是我们要的——日历视图上 9 月每一周的周一都得画出来。
 *
 * @property eventId    所属事件的 id
 * @property startMillis 开始时间戳（毫秒）
 * @property endMillis   结束时间戳（毫秒）
 * @property allDay      是否为全天日程（全天的不显示具体时间）
 * @property calendarId  属于哪个日历
 * @property calendarName  所属日历名称（用于分组显示）
 * @property calendarColor 所属日历颜色
 */
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarId: Long,
    val calendarName: String,
    val calendarColor: Int
)
