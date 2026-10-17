"""预测完成后的大模型解析，所有模型调用统一由Python Agent执行。"""

from pydantic import BaseModel, Field

from gas_forecast_agent.config import Settings
from gas_forecast_agent.llm import build_llm_client


class ForecastAnalysisRequest(BaseModel):
    forecast_batch_no: str = Field(alias="forecastBatchNo", min_length=1, max_length=64)
    agent_code: str = Field(alias="agentCode", min_length=1, max_length=64)
    prompt: str = Field(min_length=1, max_length=120_000)


class ForecastAnalysisResponse(BaseModel):
    forecast_batch_no: str = Field(alias="forecastBatchNo")
    analysis_content: str = Field(alias="analysisContent")
    llm_model: str = Field(alias="llmModel")


class ForecastResultAnalyzer:
    """调用项目统一配置的千问/OpenAI兼容模型生成预测分析。"""

    def __init__(self, settings: Settings) -> None:
        if not settings.llm_api_key or not settings.llm_model:
            raise ValueError("预测分析需要配置LLM_API_KEY和LLM_MODEL")
        self._model = settings.llm_model
        self._client = build_llm_client(settings, minimum_timeout=120)

    async def analyze(self, request: ForecastAnalysisRequest) -> ForecastAnalysisResponse:
        response = await self._client.chat.completions.create(
            model=self._model,
            temperature=0.2,
            messages=[
                {
                    "role": "system",
                    "content": self._system_prompt(request.agent_code),
                },
                {"role": "user", "content": request.prompt},
            ],
        )
        content = (response.choices[0].message.content or "").strip()
        if not content:
            raise RuntimeError("大模型未返回预测分析内容")
        return ForecastAnalysisResponse(
            forecastBatchNo=request.forecast_batch_no,
            analysisContent=content,
            llmModel=self._model,
        )

    @staticmethod
    def _system_prompt(agent_code: str) -> str:
        role = {
            "short-term": "你是短期客户天然气需求预测分析专家。",
            "monthly-sales": "你是月度天然气销量预测分析专家。",
            "winter-supply": "你是冬季天然气保供预测分析专家。",
        }.get(agent_code, "你是天然气需求预测分析专家。")
        return (
            role
            + "只能依据请求中提供的数据进行解释，不得编造日期、数值、天气、价格、库存或业务事件；"
            "不得重新生成预测值。定量结论必须能追溯到输入字段，证据不足时明确说明无法判断。"
        )
