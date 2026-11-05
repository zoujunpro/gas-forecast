# 中国节假日数据

`cn-2004-2026.json` 是模型运行时使用的本地中国法定节假日与调休补班数据。

- 覆盖范围：2004—2026年。
- 上游数据：`vsme/chinese-days`。
- 当前上游版本：`1.5.9`。
- 许可证：MIT。
- `holidays` 表示实际休息日期，`workdays` 表示周末调休补班日期。
- 普通周末不逐日存储，由 `LocalHolidayProvider` 根据星期动态判断。

更新下一年度数据时，应同步修改 JSON 中的 `coverage`、`source.version`、`holidays`
和 `workdays`，并运行全部测试。国务院尚未公布的年度不得写入确认数据；超出覆盖范围的
日期会返回 `holiday_calendar_confirmed=0`。
