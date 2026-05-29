#pragma once
#include <Arduino.h>

enum AlertType {
  ALERT_NONE = 0,
  ALERT_WATER,
  ALERT_MEDICINE,
  ALERT_APPOINTMENT,
  ALERT_CUSTOM
};

struct AlertItem {
  AlertType type;
  String message;
  unsigned long duration;
};

bool enqueueAlert(AlertItem item);
bool dequeueAlert(AlertItem &item);
bool hasPendingAlert();