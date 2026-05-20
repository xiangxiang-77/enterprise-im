import { format, isThisWeek, isThisYear, isToday, isYesterday } from "date-fns"
import { zhCN } from "date-fns/locale"

export const formatTime = (timestamp: number) => {
  const date = new Date(timestamp)
  if (isToday(date)) return format(date, "HH:mm")
  if (isYesterday(date)) return "昨天"
  if (isThisWeek(date)) return format(date, "EEEE", { locale: zhCN })
  if (isThisYear(date)) return format(date, "MM-dd")
  return format(date, "yyyy-MM-dd")
}
