package com.gas.forecast.dao.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 只读取同地区的原始旬销量，不从日销量换算。 */
public interface ForecastTendayComparisonMapper {
    @Select("""
            <script>
            select date_format(stat_date, '%Y-%m-%d') as date, gas_sales as value
            from data_winter_tenday_dataset_tb
            where region_code = #{regionCode} and stat_date in
            <foreach collection="dates" item="date" open="(" separator="," close=")">#{date}</foreach>
            </script>
            """)
    List<Map<String, Object>> selectComparison(@Param("regionCode") String regionCode, @Param("dates") List<String> dates);
}
