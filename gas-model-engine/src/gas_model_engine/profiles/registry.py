from __future__ import annotations

from collections.abc import Iterable

from .contracts import AlgorithmProfile


class AlgorithmProfileRegistry:
    def __init__(self, profiles: Iterable[AlgorithmProfile] = ()) -> None:
        self._profiles = {}
        for profile in profiles:
            self.register(profile)

    def register(self, profile: AlgorithmProfile) -> None:
        if profile.code in self._profiles:
            raise ValueError(f"算法方案已注册: {profile.code}")
        candidate_ids = [item.id for item in profile.candidates]
        if len(candidate_ids) != len(set(candidate_ids)):
            raise ValueError(f"算法方案存在重复候选标识: {profile.code}")
        self._profiles[profile.code] = profile

    def get(self, profile_code: str) -> AlgorithmProfile:
        try:
            return self._profiles[profile_code]
        except KeyError as exc:
            supported = ", ".join(sorted(self._profiles))
            raise KeyError(f"算法方案不存在: {profile_code}; supported: {supported}") from exc

    def list_profile_codes(self) -> list[str]:
        return sorted(self._profiles)
