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
@TableName(value = "model_train_feature_data_tb")
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
    @TableField(value = "feature_001")
    private Double feature001;

    /**
     * 模型特征槽位002
     */
    @TableField(value = "feature_002")
    private Double feature002;

    /**
     * 模型特征槽位003
     */
    @TableField(value = "feature_003")
    private Double feature003;

    /**
     * 模型特征槽位004
     */
    @TableField(value = "feature_004")
    private Double feature004;

    /**
     * 模型特征槽位005
     */
    @TableField(value = "feature_005")
    private Double feature005;

    /**
     * 模型特征槽位006
     */
    @TableField(value = "feature_006")
    private Double feature006;

    /**
     * 模型特征槽位007
     */
    @TableField(value = "feature_007")
    private Double feature007;

    /**
     * 模型特征槽位008
     */
    @TableField(value = "feature_008")
    private Double feature008;

    /**
     * 模型特征槽位009
     */
    @TableField(value = "feature_009")
    private Double feature009;

    /**
     * 模型特征槽位010
     */
    @TableField(value = "feature_010")
    private Double feature010;

    /**
     * 模型特征槽位011
     */
    @TableField(value = "feature_011")
    private Double feature011;

    /**
     * 模型特征槽位012
     */
    @TableField(value = "feature_012")
    private Double feature012;

    /**
     * 模型特征槽位013
     */
    @TableField(value = "feature_013")
    private Double feature013;

    /**
     * 模型特征槽位014
     */
    @TableField(value = "feature_014")
    private Double feature014;

    /**
     * 模型特征槽位015
     */
    @TableField(value = "feature_015")
    private Double feature015;

    /**
     * 模型特征槽位016
     */
    @TableField(value = "feature_016")
    private Double feature016;

    /**
     * 模型特征槽位017
     */
    @TableField(value = "feature_017")
    private Double feature017;

    /**
     * 模型特征槽位018
     */
    @TableField(value = "feature_018")
    private Double feature018;

    /**
     * 模型特征槽位019
     */
    @TableField(value = "feature_019")
    private Double feature019;

    /**
     * 模型特征槽位020
     */
    @TableField(value = "feature_020")
    private Double feature020;

    /**
     * 模型特征槽位021
     */
    @TableField(value = "feature_021")
    private Double feature021;

    /**
     * 模型特征槽位022
     */
    @TableField(value = "feature_022")
    private Double feature022;

    /**
     * 模型特征槽位023
     */
    @TableField(value = "feature_023")
    private Double feature023;

    /**
     * 模型特征槽位024
     */
    @TableField(value = "feature_024")
    private Double feature024;

    /**
     * 模型特征槽位025
     */
    @TableField(value = "feature_025")
    private Double feature025;

    /**
     * 模型特征槽位026
     */
    @TableField(value = "feature_026")
    private Double feature026;

    /**
     * 模型特征槽位027
     */
    @TableField(value = "feature_027")
    private Double feature027;

    /**
     * 模型特征槽位028
     */
    @TableField(value = "feature_028")
    private Double feature028;

    /**
     * 模型特征槽位029
     */
    @TableField(value = "feature_029")
    private Double feature029;

    /**
     * 模型特征槽位030
     */
    @TableField(value = "feature_030")
    private Double feature030;

    /**
     * 模型特征槽位031
     */
    @TableField(value = "feature_031")
    private Double feature031;

    /**
     * 模型特征槽位032
     */
    @TableField(value = "feature_032")
    private Double feature032;

    /**
     * 模型特征槽位033
     */
    @TableField(value = "feature_033")
    private Double feature033;

    /**
     * 模型特征槽位034
     */
    @TableField(value = "feature_034")
    private Double feature034;

    /**
     * 模型特征槽位035
     */
    @TableField(value = "feature_035")
    private Double feature035;

    /**
     * 模型特征槽位036
     */
    @TableField(value = "feature_036")
    private Double feature036;

    /**
     * 模型特征槽位037
     */
    @TableField(value = "feature_037")
    private Double feature037;

    /**
     * 模型特征槽位038
     */
    @TableField(value = "feature_038")
    private Double feature038;

    /**
     * 模型特征槽位039
     */
    @TableField(value = "feature_039")
    private Double feature039;

    /**
     * 模型特征槽位040
     */
    @TableField(value = "feature_040")
    private Double feature040;

    /**
     * 模型特征槽位041
     */
    @TableField(value = "feature_041")
    private Double feature041;

    /**
     * 模型特征槽位042
     */
    @TableField(value = "feature_042")
    private Double feature042;

    /**
     * 模型特征槽位043
     */
    @TableField(value = "feature_043")
    private Double feature043;

    /**
     * 模型特征槽位044
     */
    @TableField(value = "feature_044")
    private Double feature044;

    /**
     * 模型特征槽位045
     */
    @TableField(value = "feature_045")
    private Double feature045;

    /**
     * 模型特征槽位046
     */
    @TableField(value = "feature_046")
    private Double feature046;

    /**
     * 模型特征槽位047
     */
    @TableField(value = "feature_047")
    private Double feature047;

    /**
     * 模型特征槽位048
     */
    @TableField(value = "feature_048")
    private Double feature048;

    /**
     * 模型特征槽位049
     */
    @TableField(value = "feature_049")
    private Double feature049;

    /**
     * 模型特征槽位050
     */
    @TableField(value = "feature_050")
    private Double feature050;

    /**
     * 模型特征槽位051
     */
    @TableField(value = "feature_051")
    private Double feature051;

    /**
     * 模型特征槽位052
     */
    @TableField(value = "feature_052")
    private Double feature052;

    /**
     * 模型特征槽位053
     */
    @TableField(value = "feature_053")
    private Double feature053;

    /**
     * 模型特征槽位054
     */
    @TableField(value = "feature_054")
    private Double feature054;

    /**
     * 模型特征槽位055
     */
    @TableField(value = "feature_055")
    private Double feature055;

    /**
     * 模型特征槽位056
     */
    @TableField(value = "feature_056")
    private Double feature056;

    /**
     * 模型特征槽位057
     */
    @TableField(value = "feature_057")
    private Double feature057;

    /**
     * 模型特征槽位058
     */
    @TableField(value = "feature_058")
    private Double feature058;

    /**
     * 模型特征槽位059
     */
    @TableField(value = "feature_059")
    private Double feature059;

    /**
     * 模型特征槽位060
     */
    @TableField(value = "feature_060")
    private Double feature060;

    /**
     * 模型特征槽位061
     */
    @TableField(value = "feature_061")
    private Double feature061;

    /**
     * 模型特征槽位062
     */
    @TableField(value = "feature_062")
    private Double feature062;

    /**
     * 模型特征槽位063
     */
    @TableField(value = "feature_063")
    private Double feature063;

    /**
     * 模型特征槽位064
     */
    @TableField(value = "feature_064")
    private Double feature064;

    /**
     * 模型特征槽位065
     */
    @TableField(value = "feature_065")
    private Double feature065;

    /**
     * 模型特征槽位066
     */
    @TableField(value = "feature_066")
    private Double feature066;

    /**
     * 模型特征槽位067
     */
    @TableField(value = "feature_067")
    private Double feature067;

    /**
     * 模型特征槽位068
     */
    @TableField(value = "feature_068")
    private Double feature068;

    /**
     * 模型特征槽位069
     */
    @TableField(value = "feature_069")
    private Double feature069;

    /**
     * 模型特征槽位070
     */
    @TableField(value = "feature_070")
    private Double feature070;

    /**
     * 模型特征槽位071
     */
    @TableField(value = "feature_071")
    private Double feature071;

    /**
     * 模型特征槽位072
     */
    @TableField(value = "feature_072")
    private Double feature072;

    /**
     * 模型特征槽位073
     */
    @TableField(value = "feature_073")
    private Double feature073;

    /**
     * 模型特征槽位074
     */
    @TableField(value = "feature_074")
    private Double feature074;

    /**
     * 模型特征槽位075
     */
    @TableField(value = "feature_075")
    private Double feature075;

    /**
     * 模型特征槽位076
     */
    @TableField(value = "feature_076")
    private Double feature076;

    /**
     * 模型特征槽位077
     */
    @TableField(value = "feature_077")
    private Double feature077;

    /**
     * 模型特征槽位078
     */
    @TableField(value = "feature_078")
    private Double feature078;

    /**
     * 模型特征槽位079
     */
    @TableField(value = "feature_079")
    private Double feature079;

    /**
     * 模型特征槽位080
     */
    @TableField(value = "feature_080")
    private Double feature080;

    /**
     * 模型特征槽位081
     */
    @TableField(value = "feature_081")
    private Double feature081;

    /**
     * 模型特征槽位082
     */
    @TableField(value = "feature_082")
    private Double feature082;

    /**
     * 模型特征槽位083
     */
    @TableField(value = "feature_083")
    private Double feature083;

    /**
     * 模型特征槽位084
     */
    @TableField(value = "feature_084")
    private Double feature084;

    /**
     * 模型特征槽位085
     */
    @TableField(value = "feature_085")
    private Double feature085;

    /**
     * 模型特征槽位086
     */
    @TableField(value = "feature_086")
    private Double feature086;

    /**
     * 模型特征槽位087
     */
    @TableField(value = "feature_087")
    private Double feature087;

    /**
     * 模型特征槽位088
     */
    @TableField(value = "feature_088")
    private Double feature088;

    /**
     * 模型特征槽位089
     */
    @TableField(value = "feature_089")
    private Double feature089;

    /**
     * 模型特征槽位090
     */
    @TableField(value = "feature_090")
    private Double feature090;

    /**
     * 模型特征槽位091
     */
    @TableField(value = "feature_091")
    private Double feature091;

    /**
     * 模型特征槽位092
     */
    @TableField(value = "feature_092")
    private Double feature092;

    /**
     * 模型特征槽位093
     */
    @TableField(value = "feature_093")
    private Double feature093;

    /**
     * 模型特征槽位094
     */
    @TableField(value = "feature_094")
    private Double feature094;

    /**
     * 模型特征槽位095
     */
    @TableField(value = "feature_095")
    private Double feature095;

    /**
     * 模型特征槽位096
     */
    @TableField(value = "feature_096")
    private Double feature096;

    /**
     * 模型特征槽位097
     */
    @TableField(value = "feature_097")
    private Double feature097;

    /**
     * 模型特征槽位098
     */
    @TableField(value = "feature_098")
    private Double feature098;

    /**
     * 模型特征槽位099
     */
    @TableField(value = "feature_099")
    private Double feature099;

    /**
     * 模型特征槽位100
     */
    @TableField(value = "feature_100")
    private Double feature100;

    /**
     * 模型特征槽位101
     */
    @TableField(value = "feature_101")
    private Double feature101;

    /**
     * 模型特征槽位102
     */
    @TableField(value = "feature_102")
    private Double feature102;

    /**
     * 模型特征槽位103
     */
    @TableField(value = "feature_103")
    private Double feature103;

    /**
     * 模型特征槽位104
     */
    @TableField(value = "feature_104")
    private Double feature104;

    /**
     * 模型特征槽位105
     */
    @TableField(value = "feature_105")
    private Double feature105;

    /**
     * 模型特征槽位106
     */
    @TableField(value = "feature_106")
    private Double feature106;

    /**
     * 模型特征槽位107
     */
    @TableField(value = "feature_107")
    private Double feature107;

    /**
     * 模型特征槽位108
     */
    @TableField(value = "feature_108")
    private Double feature108;

    /**
     * 模型特征槽位109
     */
    @TableField(value = "feature_109")
    private Double feature109;

    /**
     * 模型特征槽位110
     */
    @TableField(value = "feature_110")
    private Double feature110;

    /**
     * 模型特征槽位111
     */
    @TableField(value = "feature_111")
    private Double feature111;

    /**
     * 模型特征槽位112
     */
    @TableField(value = "feature_112")
    private Double feature112;

    /**
     * 模型特征槽位113
     */
    @TableField(value = "feature_113")
    private Double feature113;

    /**
     * 模型特征槽位114
     */
    @TableField(value = "feature_114")
    private Double feature114;

    /**
     * 模型特征槽位115
     */
    @TableField(value = "feature_115")
    private Double feature115;

    /**
     * 模型特征槽位116
     */
    @TableField(value = "feature_116")
    private Double feature116;

    /**
     * 模型特征槽位117
     */
    @TableField(value = "feature_117")
    private Double feature117;

    /**
     * 模型特征槽位118
     */
    @TableField(value = "feature_118")
    private Double feature118;

    /**
     * 模型特征槽位119
     */
    @TableField(value = "feature_119")
    private Double feature119;

    /**
     * 模型特征槽位120
     */
    @TableField(value = "feature_120")
    private Double feature120;

    /**
     * 模型特征槽位121
     */
    @TableField(value = "feature_121")
    private Double feature121;

    /**
     * 模型特征槽位122
     */
    @TableField(value = "feature_122")
    private Double feature122;

    /**
     * 模型特征槽位123
     */
    @TableField(value = "feature_123")
    private Double feature123;

    /**
     * 模型特征槽位124
     */
    @TableField(value = "feature_124")
    private Double feature124;

    /**
     * 模型特征槽位125
     */
    @TableField(value = "feature_125")
    private Double feature125;

    /**
     * 模型特征槽位126
     */
    @TableField(value = "feature_126")
    private Double feature126;

    /**
     * 模型特征槽位127
     */
    @TableField(value = "feature_127")
    private Double feature127;

    /**
     * 模型特征槽位128
     */
    @TableField(value = "feature_128")
    private Double feature128;

    /**
     * 模型特征槽位129
     */
    @TableField(value = "feature_129")
    private Double feature129;

    /**
     * 模型特征槽位130
     */
    @TableField(value = "feature_130")
    private Double feature130;

    /**
     * 模型特征槽位131
     */
    @TableField(value = "feature_131")
    private Double feature131;

    /**
     * 模型特征槽位132
     */
    @TableField(value = "feature_132")
    private Double feature132;

    /**
     * 模型特征槽位133
     */
    @TableField(value = "feature_133")
    private Double feature133;

    /**
     * 模型特征槽位134
     */
    @TableField(value = "feature_134")
    private Double feature134;

    /**
     * 模型特征槽位135
     */
    @TableField(value = "feature_135")
    private Double feature135;

    /**
     * 模型特征槽位136
     */
    @TableField(value = "feature_136")
    private Double feature136;

    /**
     * 模型特征槽位137
     */
    @TableField(value = "feature_137")
    private Double feature137;

    /**
     * 模型特征槽位138
     */
    @TableField(value = "feature_138")
    private Double feature138;

    /**
     * 模型特征槽位139
     */
    @TableField(value = "feature_139")
    private Double feature139;

    /**
     * 模型特征槽位140
     */
    @TableField(value = "feature_140")
    private Double feature140;

    /**
     * 模型特征槽位141
     */
    @TableField(value = "feature_141")
    private Double feature141;

    /**
     * 模型特征槽位142
     */
    @TableField(value = "feature_142")
    private Double feature142;

    /**
     * 模型特征槽位143
     */
    @TableField(value = "feature_143")
    private Double feature143;

    /**
     * 模型特征槽位144
     */
    @TableField(value = "feature_144")
    private Double feature144;

    /**
     * 模型特征槽位145
     */
    @TableField(value = "feature_145")
    private Double feature145;

    /**
     * 模型特征槽位146
     */
    @TableField(value = "feature_146")
    private Double feature146;

    /**
     * 模型特征槽位147
     */
    @TableField(value = "feature_147")
    private Double feature147;

    /**
     * 模型特征槽位148
     */
    @TableField(value = "feature_148")
    private Double feature148;

    /**
     * 模型特征槽位149
     */
    @TableField(value = "feature_149")
    private Double feature149;

    /**
     * 模型特征槽位150
     */
    @TableField(value = "feature_150")
    private Double feature150;

    /**
     * 模型特征槽位151
     */
    @TableField(value = "feature_151")
    private Double feature151;

    /**
     * 模型特征槽位152
     */
    @TableField(value = "feature_152")
    private Double feature152;

    /**
     * 模型特征槽位153
     */
    @TableField(value = "feature_153")
    private Double feature153;

    /**
     * 模型特征槽位154
     */
    @TableField(value = "feature_154")
    private Double feature154;

    /**
     * 模型特征槽位155
     */
    @TableField(value = "feature_155")
    private Double feature155;

    /**
     * 模型特征槽位156
     */
    @TableField(value = "feature_156")
    private Double feature156;

    /**
     * 模型特征槽位157
     */
    @TableField(value = "feature_157")
    private Double feature157;

    /**
     * 模型特征槽位158
     */
    @TableField(value = "feature_158")
    private Double feature158;

    /**
     * 模型特征槽位159
     */
    @TableField(value = "feature_159")
    private Double feature159;

    /**
     * 模型特征槽位160
     */
    @TableField(value = "feature_160")
    private Double feature160;

    /**
     * 模型特征槽位161
     */
    @TableField(value = "feature_161")
    private Double feature161;

    /**
     * 模型特征槽位162
     */
    @TableField(value = "feature_162")
    private Double feature162;

    /**
     * 模型特征槽位163
     */
    @TableField(value = "feature_163")
    private Double feature163;

    /**
     * 模型特征槽位164
     */
    @TableField(value = "feature_164")
    private Double feature164;

    /**
     * 模型特征槽位165
     */
    @TableField(value = "feature_165")
    private Double feature165;

    /**
     * 模型特征槽位166
     */
    @TableField(value = "feature_166")
    private Double feature166;

    /**
     * 模型特征槽位167
     */
    @TableField(value = "feature_167")
    private Double feature167;

    /**
     * 模型特征槽位168
     */
    @TableField(value = "feature_168")
    private Double feature168;

    /**
     * 模型特征槽位169
     */
    @TableField(value = "feature_169")
    private Double feature169;

    /**
     * 模型特征槽位170
     */
    @TableField(value = "feature_170")
    private Double feature170;

    /**
     * 模型特征槽位171
     */
    @TableField(value = "feature_171")
    private Double feature171;

    /**
     * 模型特征槽位172
     */
    @TableField(value = "feature_172")
    private Double feature172;

    /**
     * 模型特征槽位173
     */
    @TableField(value = "feature_173")
    private Double feature173;

    /**
     * 模型特征槽位174
     */
    @TableField(value = "feature_174")
    private Double feature174;

    /**
     * 模型特征槽位175
     */
    @TableField(value = "feature_175")
    private Double feature175;

    /**
     * 模型特征槽位176
     */
    @TableField(value = "feature_176")
    private Double feature176;

    /**
     * 模型特征槽位177
     */
    @TableField(value = "feature_177")
    private Double feature177;

    /**
     * 模型特征槽位178
     */
    @TableField(value = "feature_178")
    private Double feature178;

    /**
     * 模型特征槽位179
     */
    @TableField(value = "feature_179")
    private Double feature179;

    /**
     * 模型特征槽位180
     */
    @TableField(value = "feature_180")
    private Double feature180;

    /**
     * 模型特征槽位181
     */
    @TableField(value = "feature_181")
    private Double feature181;

    /**
     * 模型特征槽位182
     */
    @TableField(value = "feature_182")
    private Double feature182;

    /**
     * 模型特征槽位183
     */
    @TableField(value = "feature_183")
    private Double feature183;

    /**
     * 模型特征槽位184
     */
    @TableField(value = "feature_184")
    private Double feature184;

    /**
     * 模型特征槽位185
     */
    @TableField(value = "feature_185")
    private Double feature185;

    /**
     * 模型特征槽位186
     */
    @TableField(value = "feature_186")
    private Double feature186;

    /**
     * 模型特征槽位187
     */
    @TableField(value = "feature_187")
    private Double feature187;

    /**
     * 模型特征槽位188
     */
    @TableField(value = "feature_188")
    private Double feature188;

    /**
     * 模型特征槽位189
     */
    @TableField(value = "feature_189")
    private Double feature189;

    /**
     * 模型特征槽位190
     */
    @TableField(value = "feature_190")
    private Double feature190;

    /**
     * 模型特征槽位191
     */
    @TableField(value = "feature_191")
    private Double feature191;

    /**
     * 模型特征槽位192
     */
    @TableField(value = "feature_192")
    private Double feature192;

    /**
     * 模型特征槽位193
     */
    @TableField(value = "feature_193")
    private Double feature193;

    /**
     * 模型特征槽位194
     */
    @TableField(value = "feature_194")
    private Double feature194;

    /**
     * 模型特征槽位195
     */
    @TableField(value = "feature_195")
    private Double feature195;

    /**
     * 模型特征槽位196
     */
    @TableField(value = "feature_196")
    private Double feature196;

    /**
     * 模型特征槽位197
     */
    @TableField(value = "feature_197")
    private Double feature197;

    /**
     * 模型特征槽位198
     */
    @TableField(value = "feature_198")
    private Double feature198;

    /**
     * 模型特征槽位199
     */
    @TableField(value = "feature_199")
    private Double feature199;

    /**
     * 模型特征槽位200
     */
    @TableField(value = "feature_200")
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
