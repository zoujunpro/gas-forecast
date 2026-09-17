package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 天然气预测模型统一训练特征宽表
 * @TableName model_train_feature_data_tb
 */
@TableName(value ="model_train_feature_data_tb")
@Data
public class ModelTrainFeatureDataTb {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 统计日期
     */
    private String statDate;

    /**
     * 时间粒度 DAY/TENDAY/MONTH
     */
    private String timeGranularity;

    /**
     * 区域编码
     */
    private String regionCode;

    /**
     * 区域名称
     */
    private String regionName;

    /**
     * 客户编码
     */
    private String customerCode;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 行业编码
     */
    private String industryCode;

    /**
     * 行业名称
     */
    private String industryName;

    /**
     * 天然气销量(预测目标)
     */
    private BigDecimal gasSales;

    /**
     * 模型特征槽位001
     */
    private Double feature001;

    /**
     * 模型特征槽位002
     */
    private Double feature002;

    /**
     * 模型特征槽位003
     */
    private Double feature003;

    /**
     * 模型特征槽位004
     */
    private Double feature004;

    /**
     * 模型特征槽位005
     */
    private Double feature005;

    /**
     * 模型特征槽位006
     */
    private Double feature006;

    /**
     * 模型特征槽位007
     */
    private Double feature007;

    /**
     * 模型特征槽位008
     */
    private Double feature008;

    /**
     * 模型特征槽位009
     */
    private Double feature009;

    /**
     * 模型特征槽位010
     */
    private Double feature010;

    /**
     * 模型特征槽位011
     */
    private Double feature011;

    /**
     * 模型特征槽位012
     */
    private Double feature012;

    /**
     * 模型特征槽位013
     */
    private Double feature013;

    /**
     * 模型特征槽位014
     */
    private Double feature014;

    /**
     * 模型特征槽位015
     */
    private Double feature015;

    /**
     * 模型特征槽位016
     */
    private Double feature016;

    /**
     * 模型特征槽位017
     */
    private Double feature017;

    /**
     * 模型特征槽位018
     */
    private Double feature018;

    /**
     * 模型特征槽位019
     */
    private Double feature019;

    /**
     * 模型特征槽位020
     */
    private Double feature020;

    /**
     * 模型特征槽位021
     */
    private Double feature021;

    /**
     * 模型特征槽位022
     */
    private Double feature022;

    /**
     * 模型特征槽位023
     */
    private Double feature023;

    /**
     * 模型特征槽位024
     */
    private Double feature024;

    /**
     * 模型特征槽位025
     */
    private Double feature025;

    /**
     * 模型特征槽位026
     */
    private Double feature026;

    /**
     * 模型特征槽位027
     */
    private Double feature027;

    /**
     * 模型特征槽位028
     */
    private Double feature028;

    /**
     * 模型特征槽位029
     */
    private Double feature029;

    /**
     * 模型特征槽位030
     */
    private Double feature030;

    /**
     * 模型特征槽位031
     */
    private Double feature031;

    /**
     * 模型特征槽位032
     */
    private Double feature032;

    /**
     * 模型特征槽位033
     */
    private Double feature033;

    /**
     * 模型特征槽位034
     */
    private Double feature034;

    /**
     * 模型特征槽位035
     */
    private Double feature035;

    /**
     * 模型特征槽位036
     */
    private Double feature036;

    /**
     * 模型特征槽位037
     */
    private Double feature037;

    /**
     * 模型特征槽位038
     */
    private Double feature038;

    /**
     * 模型特征槽位039
     */
    private Double feature039;

    /**
     * 模型特征槽位040
     */
    private Double feature040;

    /**
     * 模型特征槽位041
     */
    private Double feature041;

    /**
     * 模型特征槽位042
     */
    private Double feature042;

    /**
     * 模型特征槽位043
     */
    private Double feature043;

    /**
     * 模型特征槽位044
     */
    private Double feature044;

    /**
     * 模型特征槽位045
     */
    private Double feature045;

    /**
     * 模型特征槽位046
     */
    private Double feature046;

    /**
     * 模型特征槽位047
     */
    private Double feature047;

    /**
     * 模型特征槽位048
     */
    private Double feature048;

    /**
     * 模型特征槽位049
     */
    private Double feature049;

    /**
     * 模型特征槽位050
     */
    private Double feature050;

    /**
     * 模型特征槽位051
     */
    private Double feature051;

    /**
     * 模型特征槽位052
     */
    private Double feature052;

    /**
     * 模型特征槽位053
     */
    private Double feature053;

    /**
     * 模型特征槽位054
     */
    private Double feature054;

    /**
     * 模型特征槽位055
     */
    private Double feature055;

    /**
     * 模型特征槽位056
     */
    private Double feature056;

    /**
     * 模型特征槽位057
     */
    private Double feature057;

    /**
     * 模型特征槽位058
     */
    private Double feature058;

    /**
     * 模型特征槽位059
     */
    private Double feature059;

    /**
     * 模型特征槽位060
     */
    private Double feature060;

    /**
     * 模型特征槽位061
     */
    private Double feature061;

    /**
     * 模型特征槽位062
     */
    private Double feature062;

    /**
     * 模型特征槽位063
     */
    private Double feature063;

    /**
     * 模型特征槽位064
     */
    private Double feature064;

    /**
     * 模型特征槽位065
     */
    private Double feature065;

    /**
     * 模型特征槽位066
     */
    private Double feature066;

    /**
     * 模型特征槽位067
     */
    private Double feature067;

    /**
     * 模型特征槽位068
     */
    private Double feature068;

    /**
     * 模型特征槽位069
     */
    private Double feature069;

    /**
     * 模型特征槽位070
     */
    private Double feature070;

    /**
     * 模型特征槽位071
     */
    private Double feature071;

    /**
     * 模型特征槽位072
     */
    private Double feature072;

    /**
     * 模型特征槽位073
     */
    private Double feature073;

    /**
     * 模型特征槽位074
     */
    private Double feature074;

    /**
     * 模型特征槽位075
     */
    private Double feature075;

    /**
     * 模型特征槽位076
     */
    private Double feature076;

    /**
     * 模型特征槽位077
     */
    private Double feature077;

    /**
     * 模型特征槽位078
     */
    private Double feature078;

    /**
     * 模型特征槽位079
     */
    private Double feature079;

    /**
     * 模型特征槽位080
     */
    private Double feature080;

    /**
     * 模型特征槽位081
     */
    private Double feature081;

    /**
     * 模型特征槽位082
     */
    private Double feature082;

    /**
     * 模型特征槽位083
     */
    private Double feature083;

    /**
     * 模型特征槽位084
     */
    private Double feature084;

    /**
     * 模型特征槽位085
     */
    private Double feature085;

    /**
     * 模型特征槽位086
     */
    private Double feature086;

    /**
     * 模型特征槽位087
     */
    private Double feature087;

    /**
     * 模型特征槽位088
     */
    private Double feature088;

    /**
     * 模型特征槽位089
     */
    private Double feature089;

    /**
     * 模型特征槽位090
     */
    private Double feature090;

    /**
     * 模型特征槽位091
     */
    private Double feature091;

    /**
     * 模型特征槽位092
     */
    private Double feature092;

    /**
     * 模型特征槽位093
     */
    private Double feature093;

    /**
     * 模型特征槽位094
     */
    private Double feature094;

    /**
     * 模型特征槽位095
     */
    private Double feature095;

    /**
     * 模型特征槽位096
     */
    private Double feature096;

    /**
     * 模型特征槽位097
     */
    private Double feature097;

    /**
     * 模型特征槽位098
     */
    private Double feature098;

    /**
     * 模型特征槽位099
     */
    private Double feature099;

    /**
     * 模型特征槽位100
     */
    private Double feature100;

    /**
     * 模型特征槽位101
     */
    private Double feature101;

    /**
     * 模型特征槽位102
     */
    private Double feature102;

    /**
     * 模型特征槽位103
     */
    private Double feature103;

    /**
     * 模型特征槽位104
     */
    private Double feature104;

    /**
     * 模型特征槽位105
     */
    private Double feature105;

    /**
     * 模型特征槽位106
     */
    private Double feature106;

    /**
     * 模型特征槽位107
     */
    private Double feature107;

    /**
     * 模型特征槽位108
     */
    private Double feature108;

    /**
     * 模型特征槽位109
     */
    private Double feature109;

    /**
     * 模型特征槽位110
     */
    private Double feature110;

    /**
     * 模型特征槽位111
     */
    private Double feature111;

    /**
     * 模型特征槽位112
     */
    private Double feature112;

    /**
     * 模型特征槽位113
     */
    private Double feature113;

    /**
     * 模型特征槽位114
     */
    private Double feature114;

    /**
     * 模型特征槽位115
     */
    private Double feature115;

    /**
     * 模型特征槽位116
     */
    private Double feature116;

    /**
     * 模型特征槽位117
     */
    private Double feature117;

    /**
     * 模型特征槽位118
     */
    private Double feature118;

    /**
     * 模型特征槽位119
     */
    private Double feature119;

    /**
     * 模型特征槽位120
     */
    private Double feature120;

    /**
     * 模型特征槽位121
     */
    private Double feature121;

    /**
     * 模型特征槽位122
     */
    private Double feature122;

    /**
     * 模型特征槽位123
     */
    private Double feature123;

    /**
     * 模型特征槽位124
     */
    private Double feature124;

    /**
     * 模型特征槽位125
     */
    private Double feature125;

    /**
     * 模型特征槽位126
     */
    private Double feature126;

    /**
     * 模型特征槽位127
     */
    private Double feature127;

    /**
     * 模型特征槽位128
     */
    private Double feature128;

    /**
     * 模型特征槽位129
     */
    private Double feature129;

    /**
     * 模型特征槽位130
     */
    private Double feature130;

    /**
     * 模型特征槽位131
     */
    private Double feature131;

    /**
     * 模型特征槽位132
     */
    private Double feature132;

    /**
     * 模型特征槽位133
     */
    private Double feature133;

    /**
     * 模型特征槽位134
     */
    private Double feature134;

    /**
     * 模型特征槽位135
     */
    private Double feature135;

    /**
     * 模型特征槽位136
     */
    private Double feature136;

    /**
     * 模型特征槽位137
     */
    private Double feature137;

    /**
     * 模型特征槽位138
     */
    private Double feature138;

    /**
     * 模型特征槽位139
     */
    private Double feature139;

    /**
     * 模型特征槽位140
     */
    private Double feature140;

    /**
     * 模型特征槽位141
     */
    private Double feature141;

    /**
     * 模型特征槽位142
     */
    private Double feature142;

    /**
     * 模型特征槽位143
     */
    private Double feature143;

    /**
     * 模型特征槽位144
     */
    private Double feature144;

    /**
     * 模型特征槽位145
     */
    private Double feature145;

    /**
     * 模型特征槽位146
     */
    private Double feature146;

    /**
     * 模型特征槽位147
     */
    private Double feature147;

    /**
     * 模型特征槽位148
     */
    private Double feature148;

    /**
     * 模型特征槽位149
     */
    private Double feature149;

    /**
     * 模型特征槽位150
     */
    private Double feature150;

    /**
     * 模型特征槽位151
     */
    private Double feature151;

    /**
     * 模型特征槽位152
     */
    private Double feature152;

    /**
     * 模型特征槽位153
     */
    private Double feature153;

    /**
     * 模型特征槽位154
     */
    private Double feature154;

    /**
     * 模型特征槽位155
     */
    private Double feature155;

    /**
     * 模型特征槽位156
     */
    private Double feature156;

    /**
     * 模型特征槽位157
     */
    private Double feature157;

    /**
     * 模型特征槽位158
     */
    private Double feature158;

    /**
     * 模型特征槽位159
     */
    private Double feature159;

    /**
     * 模型特征槽位160
     */
    private Double feature160;

    /**
     * 模型特征槽位161
     */
    private Double feature161;

    /**
     * 模型特征槽位162
     */
    private Double feature162;

    /**
     * 模型特征槽位163
     */
    private Double feature163;

    /**
     * 模型特征槽位164
     */
    private Double feature164;

    /**
     * 模型特征槽位165
     */
    private Double feature165;

    /**
     * 模型特征槽位166
     */
    private Double feature166;

    /**
     * 模型特征槽位167
     */
    private Double feature167;

    /**
     * 模型特征槽位168
     */
    private Double feature168;

    /**
     * 模型特征槽位169
     */
    private Double feature169;

    /**
     * 模型特征槽位170
     */
    private Double feature170;

    /**
     * 模型特征槽位171
     */
    private Double feature171;

    /**
     * 模型特征槽位172
     */
    private Double feature172;

    /**
     * 模型特征槽位173
     */
    private Double feature173;

    /**
     * 模型特征槽位174
     */
    private Double feature174;

    /**
     * 模型特征槽位175
     */
    private Double feature175;

    /**
     * 模型特征槽位176
     */
    private Double feature176;

    /**
     * 模型特征槽位177
     */
    private Double feature177;

    /**
     * 模型特征槽位178
     */
    private Double feature178;

    /**
     * 模型特征槽位179
     */
    private Double feature179;

    /**
     * 模型特征槽位180
     */
    private Double feature180;

    /**
     * 模型特征槽位181
     */
    private Double feature181;

    /**
     * 模型特征槽位182
     */
    private Double feature182;

    /**
     * 模型特征槽位183
     */
    private Double feature183;

    /**
     * 模型特征槽位184
     */
    private Double feature184;

    /**
     * 模型特征槽位185
     */
    private Double feature185;

    /**
     * 模型特征槽位186
     */
    private Double feature186;

    /**
     * 模型特征槽位187
     */
    private Double feature187;

    /**
     * 模型特征槽位188
     */
    private Double feature188;

    /**
     * 模型特征槽位189
     */
    private Double feature189;

    /**
     * 模型特征槽位190
     */
    private Double feature190;

    /**
     * 模型特征槽位191
     */
    private Double feature191;

    /**
     * 模型特征槽位192
     */
    private Double feature192;

    /**
     * 模型特征槽位193
     */
    private Double feature193;

    /**
     * 模型特征槽位194
     */
    private Double feature194;

    /**
     * 模型特征槽位195
     */
    private Double feature195;

    /**
     * 模型特征槽位196
     */
    private Double feature196;

    /**
     * 模型特征槽位197
     */
    private Double feature197;

    /**
     * 模型特征槽位198
     */
    private Double feature198;

    /**
     * 模型特征槽位199
     */
    private Double feature199;

    /**
     * 模型特征槽位200
     */
    private Double feature200;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}