from gas_model_platform.core.config import load_settings


def test_application_yaml_and_environment_override(tmp_path) -> None:
    config_file = tmp_path / "application.yaml"
    config_file.write_text(
        """
app:
  name: test-platform
  version: 2.0.0
storage:
  artifact_root: /data/models
logging:
  level: DEBUG
  directory: /data/logs
  retention_days: 15
""".strip(),
        encoding="utf-8",
    )

    loaded = load_settings(
        config_file,
        environ={"GAS_MODEL_LOG_LEVEL": "WARNING"},
    )

    assert loaded.app_name == "test-platform"
    assert loaded.app_version == "2.0.0"
    assert loaded.server_host == "0.0.0.0"
    assert loaded.server_port == 8090
    assert loaded.server_reload is False
    assert str(loaded.artifact_root) == "/data/models"
    assert str(loaded.log_dir) == "/data/logs"
    assert loaded.log_level == "WARNING"
    assert loaded.log_retention_days == 15
    assert loaded.log_console_enabled is True
    assert loaded.log_file_enabled is True
    assert loaded.profile == "default"
    assert loaded.config_path == config_file


def test_profile_configuration_overrides_common_configuration(tmp_path) -> None:
    common = tmp_path / "application.yaml"
    common.write_text(
        """
profiles:
  active: dev
app:
  name: profile-test
storage:
  artifact_root: common-artifacts
logging:
  level: INFO
  directory: common-logs
  retention_days: 30
""".strip(),
        encoding="utf-8",
    )
    production = tmp_path / "application-prod.yaml"
    production.write_text(
        """
storage:
  artifact_root: /data/prod-artifacts
logging:
  directory: /data/prod-logs
  retention_days: 90
""".strip(),
        encoding="utf-8",
    )

    loaded = load_settings(
        common,
        environ={
            "GAS_MODEL_PROFILE": "prod",
            "GAS_MODEL_LOG_LEVEL": "ERROR",
        },
    )

    assert loaded.profile == "prod"
    assert loaded.app_name == "profile-test"
    assert str(loaded.artifact_root) == "/data/prod-artifacts"
    assert str(loaded.log_dir) == "/data/prod-logs"
    assert loaded.log_level == "ERROR"
    assert loaded.log_retention_days == 90
    assert loaded.log_console_enabled is True
    assert loaded.log_file_enabled is True
    assert loaded.profile_config_path == production
