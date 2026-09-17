from dataclasses import dataclass

from gas_model_platform.schemas.modeling import MetricSet


@dataclass(frozen=True)
class CandidateScore:
    model_code: str
    metrics: MetricSet


class LowestMapeSelector:
    def select(self, candidates: list[CandidateScore]) -> CandidateScore | None:
        scored = [candidate for candidate in candidates if candidate.metrics.mape is not None]
        if scored:
            return min(scored, key=lambda candidate: candidate.metrics.mape or float("inf"))
        return candidates[0] if candidates else None

