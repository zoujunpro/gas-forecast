package com.gas.forecast.business.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.gas.forecast.business.enums.AiChatContentTypeEnum;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Python、Java和Vue统一使用的会话内容块。 */
@Data
@NoArgsConstructor
public class AiChatContentBlock {

    /** 内容块类型，Vue根据该值选择展示组件。 */
    @JsonTypeId
    private AiChatContentTypeEnum type;

    /** 与type对应的结构化内容。 */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = MarkdownContentData.class, name = "MARKDOWN"),
        @JsonSubTypes.Type(value = ChartContentData.class, name = "CHART"),
        @JsonSubTypes.Type(value = TableContentData.class, name = "TABLE"),
        @JsonSubTypes.Type(value = TaskCardContentData.class, name = "TASK_CARD"),
        @JsonSubTypes.Type(value = ResultLinkContentData.class, name = "RESULT_LINK")
    })
    private ContentData data;

    public AiChatContentBlock(AiChatContentTypeEnum type, ContentData data) {
        this.type = type;
        this.data = data;
    }

    /** 所有内容数据的统一标记接口。 */
    public interface ContentData {
    }

    /** Markdown文本内容。 */
    @Data
    @NoArgsConstructor
    public static class MarkdownContentData implements ContentData {
        private String content;
    }

    /** 图表内容。 */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(value = "type")
    public static class ChartContentData implements ContentData {
        private String chartType;
        private String title;
        private String xField;
        private List<String> yFields = List.of();
        private String dataSource;
        private List<Map<String, Object>> rows = List.of();
    }

    /** 表格内容，同时承载工具执行结果的标准信封。 */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(value = "type")
    public static class TableContentData implements ContentData {
        private String title;
        private String xField;
        private List<String> yFields = List.of();
        private String dataSource;
        private Boolean success;
        private String status;
        private String resultType;
        private String message;
        private String errorCode;
        private String errorType;
        private Boolean retryable;
        private Map<String, Object> data = Map.of();
        private List<Map<String, Object>> columns = List.of();
        private List<Map<String, Object>> rows = List.of();
    }

    /** 执行任务卡片内容。 */
    @Data
    @NoArgsConstructor
    public static class TaskCardContentData implements ContentData {
        private String taskNo;
        private String relationType;
        private String title;
        private String status;
    }

    /** 业务结果详情链接。 */
    @Data
    @NoArgsConstructor
    public static class ResultLinkContentData implements ContentData {
        private String title;
        private String url;
        private String taskNo;
    }
}
