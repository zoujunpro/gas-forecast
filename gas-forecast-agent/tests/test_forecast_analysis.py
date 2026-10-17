import pytest

from gas_forecast_agent.analysis import ForecastAnalysisRequest, ForecastResultAnalyzer
from gas_forecast_agent.config import Settings


def test_analysis_request_accepts_java_camel_case_fields():
    request = ForecastAnalysisRequest.model_validate(
        {"forecastBatchNo": "F-1", "agentCode": "short-term", "prompt": "分析预测结果"}
    )
    assert request.forecast_batch_no == "F-1"
    assert request.agent_code == "short-term"


def test_analyzer_requires_llm_configuration():
    with pytest.raises(ValueError, match="LLM_API_KEY"):
        ForecastResultAnalyzer(Settings(llm_api_key=None, llm_model=None))
