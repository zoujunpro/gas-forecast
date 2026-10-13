import pytest

from gas_model_platform.services.training_registry import (
    TrainingCapacityExceeded,
    TrainingRegistry,
)


def test_registry_reports_capacity_and_running_batch() -> None:
    registry = TrainingRegistry(2)
    assert registry.capacity() == {
        "max_concurrency": 2,
        "running_count": 0,
        "available_slots": 2,
    }

    with registry.running("BATCH-1", "MODEL-1"):
        assert registry.contains("BATCH-1") is True
        assert registry.capacity()["available_slots"] == 1

    assert registry.contains("BATCH-1") is False
    assert registry.capacity()["available_slots"] == 2


def test_registry_releases_slot_and_task_when_training_fails() -> None:
    registry = TrainingRegistry(1)

    with pytest.raises(ValueError, match="training failed"):
        with registry.running("BATCH-1", "MODEL-1"):
            raise ValueError("training failed")

    assert registry.tasks() == []
    with registry.running("BATCH-2", "MODEL-1"):
        with pytest.raises(TrainingCapacityExceeded):
            with registry.running("BATCH-3", "MODEL-1"):
                pass
