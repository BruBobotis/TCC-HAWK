#include "alerts.h"

#define MAX_ALERT_QUEUE 5

static AlertItem alertQueue[MAX_ALERT_QUEUE];
static int queueStart = 0;
static int queueEnd = 0;

bool enqueueAlert(AlertItem item) {
  int next = (queueEnd + 1) % MAX_ALERT_QUEUE;
  if (next == queueStart) return false; // fila cheia

  alertQueue[queueEnd] = item;
  queueEnd = next;
  return true;
}

bool dequeueAlert(AlertItem &item) {
  if (queueStart == queueEnd) return false;

  item = alertQueue[queueStart];
  queueStart = (queueStart + 1) % MAX_ALERT_QUEUE;
  return true;
}

bool hasPendingAlert() {
  return queueStart != queueEnd;
}