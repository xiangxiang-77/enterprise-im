UPDATE system_configs
SET config_value = 'disabled'
WHERE config_key = 'auth.sms.provider'
  AND config_value = 'demo';
