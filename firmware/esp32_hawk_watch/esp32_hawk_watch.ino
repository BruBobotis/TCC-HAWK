#include <MAX30105.h>
#include <Arduino_GFX_Library.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Wire.h>
#include <math.h>
#include "heartRate.h"
#include "spo2_algorithm.h"
#include "alerts.h"

#define GFX_BL 38
#define LCD_POWER 15
#define BTN_LEFT  0
#define BTN_RIGHT 14
#define MPU_ADDR 0x68
#define I2C_SDA 18
#define I2C_SCL 17

// =============================
// AGENDAMENTO DE ALERTAS (BLE)
// =============================
#define MAX_SCHEDULED_ALERTS 16

struct ScheduledAlert {
  AlertItem alert;
  uint8_t hour;
  uint8_t minute;
  bool fired;            
  bool valid;
};

static ScheduledAlert scheduledAlerts[MAX_SCHEDULED_ALERTS];
static int scheduledCount = 0;

Arduino_DataBus *bus = new Arduino_ESP32PAR8Q(
  7, 6, 8, 9,
  39, 40, 41, 42, 45, 46, 47, 48
);

Arduino_GFX *gfx = new Arduino_ST7789(
  bus,
  5,
  1,
  true,
  170,
  320,
  35,
  0,
  35,
  0
);

// ==========================
// MAX30102 runtime 
// ==========================
MAX30105 particleSensor;
bool maxOK = false;

const byte RATE_SIZE = 4;
byte rates[RATE_SIZE] = {0};
byte rateSpot = 0;
long lastBeat = 0;

float beatsPerMinute = 0;
int beatAvg = 0;
long irValue = 0;

int lastAlertMinuteChecked = -1;
bool fingerPresent = false;
int currentDay = 1;
int currentMonth = 1;
int currentYear = 2026;

unsigned long lastFingerSeen = 0;

float bpmFiltered = 0.0f;
float spo2Filtered = 0.0f;

void triggerLocalAlert(AlertType type, unsigned long duration, const String &message) {
  AlertItem item;
  item.type = type;
  item.duration = duration;
  item.message = message;
  enqueueAlert(item);
}

// ==========================
// BLE
// ==========================
#define DEVICE_NAME "HAWK-WATCH"

static BLEUUID SERVICE_UUID("7E400001-8F42-4A7A-BB6F-112233445566");
static BLEUUID STATUS_UUID ("7E400002-8F42-4A7A-BB6F-112233445566");
static BLEUUID SYNC_UUID   ("7E400003-8F42-4A7A-BB6F-112233445566");
static BLEUUID CMD_UUID    ("7E400004-8F42-4A7A-BB6F-112233445566");

BLEServer *pServer = nullptr;
BLEService *pService = nullptr;
BLECharacteristic *pStatusChar = nullptr;
BLECharacteristic *pSyncChar = nullptr;
BLECharacteristic *pCmdChar = nullptr;

bool bleConnected = false;
unsigned long lastBLESend = 0;

bool timeSynced = false;
unsigned long lastTimeUpdate = 0;
int currentHour = 0;
int currentMinute = 0;
int currentSecond = 0;

// ==========================
// BEACON PROXIMITY
// ==========================
BLEScan *pBLEScan = nullptr;

String nearestBeaconName = "Nenhum";
int nearestBeaconRSSI = -999;
float nearestBeaconDist = 0.0f;

const int BEACON_SCAN_TIME = 1;
const int BEACON_TX_POWER = -71;
const float BEACON_N = 3.1f;

unsigned long lastBeaconScan = 0;
bool scanRunning = false;

// ==========================
// CORES
// ==========================
#define BG_COLOR      0x0841
#define TOP_BG        0x1082
#define CARD_1        0x2104
#define CARD_2        0x2945
#define LINE_COLOR    0x39C7
#define TXT_WHITE     0xFFFF
#define TXT_SOFT      0xCE79
#define TXT_CYAN      0x867D
#define TXT_CYAN2     0x8FDF
#define OK_GREEN      0x07E0
#define WARN_ORANGE   0xFD20
#define ALERT_RED     0xF800
#define FLASH_BLUE    0x01CF
#define MENU_HL       0x03EF

const int SCREEN_W = 320;
const int SCREEN_H = 170;

// ==========================
// DADOS
// ==========================
struct WatchData {
  String hora = "00:00";
  String data = "01/01/2000";
  int bpm = 0;
  int spo2 = 0; 
  int passos = 0;
  float ax = 0.0, ay = 0.0, az = 1.0;
  float tempMPU = 0.0f; 
  bool ble = false, queda = false, dedo = false;
  String aparelho = "Aguardando";
  String sync = "Nao sincronizado";
  
};
#define MAX_BUF_LEN 100
uint32_t irBuffer[MAX_BUF_LEN];
uint32_t redBuffer[MAX_BUF_LEN];
int32_t bufferLength = MAX_BUF_LEN;

int32_t spo2Calc = 0;
int8_t validSPO2 = 0;
int32_t heartRateCalc = 0;
int8_t validHeartRate = 0;

int maxSampleIndex = 0;
bool spo2BufferReady = false;
unsigned long lastMAXCalc = 0;

WatchData dados;

AlertItem currentAlert;
bool remoteAlertActive = false;
unsigned long remoteAlertUntil = 0;

// ==========================
// MPU runtime
// ==========================
bool mpuOK = false;
unsigned long lastMPURead = 0;
unsigned long lastStepTime = 0;
unsigned long fallUntil = 0;
volatile bool uiNeedsRefresh = false;
float lastMotionMetric = 0.0;
float tempMPU = 0.0f;

// ==========================
// PASSOS - runtime
// ==========================
float stepAccMag = 1.0f;
float stepAccFiltered = 1.0f;
float stepDcEstimate = 1.0f;
float stepDynamic = 0.0f;

float stepS0 = 0.0f;
float stepS1 = 0.0f;
float stepS2 = 0.0f;

// ajuste fino
const float STEP_LPF_ALPHA = 0.20f;
const float STEP_DC_ALPHA  = 0.03f;

const float STEP_MIN_PEAK  = 0.02f;
const float STEP_MAX_PEAK  = 0.32f;

const unsigned long STEP_MIN_INTERVAL = 500;
const unsigned long STEP_MAX_INTERVAL = 1400;
unsigned long lastValidPeakTime = 0;

float totalAccG = 1.0f;

float FALL_LOW_G = 0.4f;
float FALL_IMPACT_G = 3.0f;
unsigned long FALL_WINDOW_MS = 1800;
unsigned long FALL_ALERT_MS = 4000;

bool lowGDetected = false;
unsigned long lowGTs = 0;
float impactPeakG = 0.0f;
unsigned long impactPeakTs = 0;

// ==========================
// ESTADOS
// ==========================
enum AppState {
  STATE_WATCH,
  STATE_MENU,
  STATE_BRIGHTNESS,
  STATE_MEASURE,
  STATE_ALERT_FALL,
  STATE_REMOTE_ALERT,
  STATE_ABOUT
};
AppState appState = STATE_WATCH;

enum Screen {
  SCREEN_HOME = 0,
  SCREEN_HEALTH,
  SCREEN_MOTION,
  SCREEN_CONNECTION
};
Screen currentScreen = SCREEN_HOME;

// ==========================
// MENU
// ==========================
const char* menuItems[] = {
  "Voltar",
  "Brilho",
  "Medicao",
  "Simular queda",
  "Sobre"
};
const int menuCount = 5;
int menuIndex = 0;

// ==========================
// BRILHO
// ==========================
int brightnessIndex = 3;
const int brightnessLevels[] = {64, 128, 192, 255};

// ==========================
// BOTÕES
// ==========================
bool lastLeftState = HIGH;
bool lastRightState = HIGH;
unsigned long lastDebounce = 0;
unsigned long leftPressStart = 0;
unsigned long rightPressStart = 0;
bool leftLongHandled = false;
bool rightLongHandled = false;

const unsigned long debounceDelay = 120;
const unsigned long longPressTime = 650;

// ==========================
// TEXTO
// ==========================
void resetText() {
  gfx->setFont();
  gfx->setTextWrap(false);
  gfx->setTextSize(1);
}

void text(int x, int y, const String &s, uint16_t color, uint8_t size = 1) {
  resetText();
  gfx->setTextColor(color);
  gfx->setTextSize(size);
  gfx->setCursor(x, y);
  gfx->print(s);
}

void textCenter(int cx, int y, const String &s, uint16_t color, uint8_t size = 1) {
  resetText();
  gfx->setTextColor(color);
  gfx->setTextSize(size);
  int w = s.length() * 6 * size;
  int x = cx - (w / 2);
  gfx->setCursor(x, y);
  gfx->print(s);
}

// ==========================
// ELEMENTOS
// ==========================
void card(int x, int y, int w, int h, uint16_t color) {
  gfx->fillRoundRect(x, y, w, h, 8, color);
}

void drawBattery(int x, int y, int percent) {
  gfx->drawRoundRect(x, y, 24, 10, 2, TXT_WHITE);
  gfx->fillRect(x + 24, y + 3, 2, 4, TXT_WHITE);

  int fill = map(percent, 0, 100, 0, 20);
  if (fill < 0) fill = 0;
  if (fill > 20) fill = 20;

  uint16_t c = OK_GREEN;
  if (percent <= 50) c = WARN_ORANGE;
  if (percent <= 20) c = ALERT_RED;
  gfx->fillRoundRect(x + 2, y + 2, fill, 6, 2, c);
}

void drawMiniBar(int x, int y, int w, int h, int value, int maxValue, uint16_t color) {
  gfx->drawRoundRect(x, y, w, h, 4, LINE_COLOR);
  int fill = map(value, 0, maxValue, 0, w - 4);
  if (fill < 0) fill = 0;
  if (fill > w - 4) fill = w - 4;
  gfx->fillRoundRect(x + 2, y + 2, fill, h - 4, 3, color);
}

void drawPill(int x, int y, const String &txt, uint16_t bg, uint16_t fg) {
  int w = 20 + txt.length() * 6;
  if (w < 42) w = 42;
  gfx->fillRoundRect(x, y, w, 16, 7, bg);
  textCenter(x + w / 2, y + 5, txt, fg, 1);
}

void drawTopBar() {
  gfx->fillRect(0, 0, SCREEN_W, 22, TOP_BG);
  gfx->drawFastHLine(0, 22, SCREEN_W, LINE_COLOR);

  gfx->fillCircle(10, 11, 3, dados.ble ? OK_GREEN : WARN_ORANGE);
  text(18, 8, dados.ble ? "BLE" : "OFF", TXT_WHITE, 1);
}

void drawFooter(const String &leftTxt, const String &rightTxt) {
  gfx->drawFastHLine(0, 152, SCREEN_W, LINE_COLOR);
  text(10, 158, leftTxt, TXT_SOFT, 1);
  text(302, 158, rightTxt, TXT_SOFT, 1);
}

void drawFooterCenter(const String &middleTxt) {
  gfx->drawFastHLine(0, 152, SCREEN_W, LINE_COLOR);
  text(10, 158, "<", TXT_SOFT, 1);
  textCenter(160, 158, middleTxt, TXT_SOFT, 1);
  text(302, 158, ">", TXT_SOFT, 1);
}

void drawSectionTitle(const String &title) {
  text(10, 28, title, TXT_CYAN2, 2);
}

void transitionFlash() {
  gfx->fillRect(0, 22, SCREEN_W, 130, FLASH_BLUE);
  delay(10);
}

void applyBrightness() {
  ledcWrite(GFX_BL, brightnessLevels[brightnessIndex]);
}

// ==========================
// MPU6050 
// ==========================
void mpuWrite(uint8_t reg, uint8_t value) {
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(reg);
  Wire.write(value);
  Wire.endTransmission();
}

bool mpuReadBytes(uint8_t reg, uint8_t count, uint8_t *dest) {
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return false;
  uint8_t received = Wire.requestFrom(MPU_ADDR, count);
  if (received != count) return false;
  for (uint8_t i = 0; i < count; i++) {
    dest[i] = Wire.read();
  }
  return true;
}

bool initMPU6050() {
  delay(50);
  uint8_t whoami = 0;
  if (!mpuReadBytes(0x75, 1, &whoami)) return false;
  if (whoami != 0x68 && whoami != 0x70) return false;

  mpuWrite(0x6B, 0x00);
  delay(10);

  mpuWrite(0x1C, 0x08);
  delay(10);

  mpuWrite(0x1B, 0x00);
  delay(10);
  mpuWrite(0x1A, 0x01);
  delay(10);

  return true;
}

float readMPUTemperature() {
  uint8_t raw[2];
  if (!mpuReadBytes(0x41, 2, raw)) {
    return -999.0;
  }

  int16_t tempRaw = (int16_t)((raw[0] << 8) | raw[1]);
  float tempC = (tempRaw / 340.0f) + 36.53f;

  return tempC;
}

void updateStepCounter(float ax, float ay, float az) {
  stepAccMag = sqrtf(ax * ax + ay * ay + az * az);
  stepAccFiltered = (STEP_LPF_ALPHA * stepAccMag) +
                    ((1.0f - STEP_LPF_ALPHA) * stepAccFiltered);
  stepDcEstimate = (STEP_DC_ALPHA * stepAccFiltered) +
                   ((1.0f - STEP_DC_ALPHA) * stepDcEstimate);
  stepDynamic = stepAccFiltered - stepDcEstimate;

  stepS0 = stepS1;
  stepS1 = stepS2;
  stepS2 = stepDynamic;

  unsigned long now = millis();
  bool isPeak = (stepS1 > stepS0) &&
                (stepS1 > stepS2) &&
                (stepS1 >= STEP_MIN_PEAK) &&
                (stepS1 <= STEP_MAX_PEAK);
  if (!isPeak) return;

  if (lastValidPeakTime == 0) {
    lastValidPeakTime = now;
    return;
  }

  unsigned long dt = now - lastValidPeakTime;

  if (dt >= STEP_MIN_INTERVAL && dt <= STEP_MAX_INTERVAL) {
    dados.passos++;
    lastStepTime = now;
  }

  lastValidPeakTime = now;
}

void updateMPUData() {
  if (millis() - lastMPURead < 2) return;
  lastMPURead = millis();

  uint8_t raw[6];
  if (!mpuReadBytes(0x3B, 6, raw)) {
    mpuOK = false;
    return;
  }

  mpuOK = true;

  int16_t axRaw = (int16_t)((raw[0] << 8) | raw[1]);
  int16_t ayRaw = (int16_t)((raw[2] << 8) | raw[3]);
  int16_t azRaw = (int16_t)((raw[4] << 8) | raw[5]);
  
  dados.ax = axRaw / 8192.0f;
  dados.ay = ayRaw / 8192.0f;
  dados.az = azRaw / 8192.0f;

  dados.tempMPU = readMPUTemperature();
  
  totalAccG = sqrtf(
    dados.ax * dados.ax +
    dados.ay * dados.ay +
    dados.az * dados.az
  );
  
  if (totalAccG > impactPeakG) {
    impactPeakG = totalAccG;
    impactPeakTs = millis();
  }

  if (millis() - impactPeakTs > 150) {
    impactPeakG = totalAccG;
  }

  updateStepCounter(dados.ax, dados.ay, dados.az);
  
  if (!lowGDetected && totalAccG < FALL_LOW_G) {
    lowGDetected = true;
    lowGTs = millis();
  }

  if (lowGDetected && (millis() - lowGTs > FALL_WINDOW_MS)) {
    lowGDetected = false;
  }

  if (lowGDetected && impactPeakG > FALL_IMPACT_G) {
    dados.queda = true;
    fallUntil = millis() + FALL_ALERT_MS;
    lowGDetected = false;
  }

  if (dados.queda && millis() > fallUntil) {
    dados.queda = false;
  }
}


// ==========================
// MAX30102 
// ==========================
bool initMAX30102() {
  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    return false;
  }

  particleSensor.setup(); 
  particleSensor.setPulseAmplitudeRed(0x20); // LED Vermelho
  particleSensor.setPulseAmplitudeIR(0x30);  // LED Infravermelho
  particleSensor.setPulseAmplitudeGreen(0);

  beatsPerMinute = 0;
  beatAvg = 0;
  rateSpot = 0;
  lastBeat = 0;
  
  maxSampleIndex = 0;
  spo2BufferReady = false;

  dados.bpm = 0;
  dados.spo2 = 0;
  dados.dedo = false;

  return true;
}

void updateMAX30102() {
  long irValue = particleSensor.getIR();
  long redValue = particleSensor.getRed();

  static unsigned long lastDebug = 0;
  if (millis() - lastDebug > 500) {
    lastDebug = millis();
  }

  if (irValue < 50000) {
    dados.dedo = false;
    dados.bpm = 0;
    dados.spo2 = 0;
    
    maxSampleIndex = 0;
    spo2BufferReady = false;
    return;
  }

  dados.dedo = true;

  if (maxSampleIndex < MAX_BUF_LEN) {
    irBuffer[maxSampleIndex] = irValue;
    redBuffer[maxSampleIndex] = redValue;
    maxSampleIndex++;
  }

  if (maxSampleIndex >= MAX_BUF_LEN) {
    spo2BufferReady = true;
    for (int i = 25; i < 100; i++) {
      irBuffer[i - 25] = irBuffer[i];
      redBuffer[i - 25] = redBuffer[i];
    }
    maxSampleIndex = 75; 
  }

  if (spo2BufferReady && millis() - lastMAXCalc > 1000) {
    lastMAXCalc = millis();
    
    maxim_heart_rate_and_oxygen_saturation(
      irBuffer, bufferLength, redBuffer,
      &spo2Calc, &validSPO2, &heartRateCalc, &validHeartRate
    );

    if (validSPO2 && spo2Calc >= 85 && spo2Calc <= 99) {
      if (dados.spo2 == 0) {
        dados.spo2 = spo2Calc; 
      } else {
        dados.spo2 = (dados.spo2 * 0.8) + (spo2Calc * 0.2);
      }
    }

    if (validHeartRate && heartRateCalc > 40 && heartRateCalc < 200) {
      if (dados.bpm == 0) {
        dados.bpm = heartRateCalc;
      } else {
        int diferenca = abs(heartRateCalc - dados.bpm);
        
        if (diferenca < 25) { 
          dados.bpm = (dados.bpm * 0.80) + (heartRateCalc * 0.20);
        }
      }
    }
  }
}
// ==========================
// BLE HELPERS
// ==========================
String makeStatusPayload() {
  char buffer[256];
  snprintf(buffer, sizeof(buffer),
      "H:%s;"
      "D:%s;"
      "BPM:%d;"
      "SPO2:%d;"
      "P:%d;"
      "Q:%d;"
      "X:%.2f;"
      "Y:%.2f;"
      "Z:%.2f;"
      "MPU:%d;"
      "MAX:%d;"
      "F:%d;",
      dados.hora.c_str(),
      dados.data.c_str(),
      dados.bpm,
      dados.spo2,
      dados.passos,
      dados.queda ? 1 : 0,
      dados.ax,
      dados.ay,
      dados.az,
      mpuOK ? 1 : 0,
      maxOK ? 1 : 0,
      dados.dedo ? 1 : 0
  );
  return String(buffer);
}

void updateDateFromSyncString(const String &syncStr) {
  if (syncStr.length() < 19) return;

  int y = syncStr.substring(0, 4).toInt();
  int m = syncStr.substring(5, 7).toInt();
  int d = syncStr.substring(8, 10).toInt();
  int h = syncStr.substring(11, 13).toInt();
  int min = syncStr.substring(14, 16).toInt();
  int ss = syncStr.substring(17, 19).toInt();

  currentYear = y;
  currentMonth = m;
  currentDay = d;
  currentHour = h;
  currentMinute = min;
  currentSecond = ss;
  char horaBuffer[6];
  char dataBuffer[11];
  sprintf(horaBuffer, "%02d:%02d", h, min);
  sprintf(dataBuffer, "%02d/%02d/%04d", d, m, y);

  dados.hora = String(horaBuffer);
  dados.data = String(dataBuffer);

  timeSynced = true;
  lastTimeUpdate = millis();
  dados.sync = "Sincronizado";
}

void updateRtcFromMillis() {
  if (!timeSynced) return;
  unsigned long now = millis();
  unsigned long elapsed = now - lastTimeUpdate;
  if (elapsed < 1000) return;

  unsigned long secondsPassed = elapsed / 1000;
  lastTimeUpdate += secondsPassed * 1000;

  while (secondsPassed--) {
    currentSecond++;
    if (currentSecond >= 60) {
      currentSecond = 0;
      currentMinute++;
      if (currentMinute >= 60) {
        currentMinute = 0;
        currentHour++;
        if (currentHour >= 24) {
          currentHour = 0;
          currentDay++;
          if (currentDay > 31) currentDay = 1;
        }
      }
    }
  }

  char horaBuffer[6];
  char dataBuffer[11];
  sprintf(horaBuffer, "%02d:%02d", currentHour, currentMinute);
  sprintf(dataBuffer, "%02d/%02d/%04d", currentDay, currentMonth, currentYear);

  dados.hora = String(horaBuffer);
  dados.data = String(dataBuffer);
}

bool scheduleAlert(const AlertItem &item, uint8_t hour, uint8_t minute) {
  if (scheduledCount >= MAX_SCHEDULED_ALERTS) return false;

  ScheduledAlert &slot = scheduledAlerts[scheduledCount];
  slot.alert = item;
  slot.hour = hour;
  slot.minute = minute;
  slot.fired = false;
  slot.valid = true;

  scheduledCount++;
  return true;
}

bool parseTimePayload(const String& payload, int& y, int& m, int& d, int& hh, int& mm) {
  String s = payload;
  s.trim();
  s.replace(" ", "|");

  int p = s.indexOf('|');
  if (p < 0) return false;

  String datePart = s.substring(0, p);
  String timePart = s.substring(p + 1);

  datePart.trim(); timePart.trim();
  if (datePart.length() < 10 || timePart.length() < 5) return false;
  d = datePart.substring(0, 2).toInt();
  m = datePart.substring(3, 5).toInt();
  y = datePart.substring(6, 10).toInt();

  hh = timePart.substring(0, 2).toInt();
  mm = timePart.substring(3, 5).toInt();

  if (y < 2020 || m < 1 || m > 12 || d < 1 || d > 31) return false;
  if (hh < 0 || hh > 23 || mm < 0 || mm > 59) return false;
  return true;
}

void handleBLECommand(String cmd) {
  cmd.trim();
  
  if (cmd == "RESET_PASSOS") {
    dados.passos = 0;
  }
  else if (cmd == "SIMULAR_QUEDA") {
    dados.queda = true;
    fallUntil = millis() + 3500;
  }
  else if (cmd == "LIMPAR_QUEDA") {
    dados.queda = false;
  }
  else if (cmd.startsWith("ALERT|")) {

    int p1 = cmd.indexOf('|');
    int p2 = cmd.indexOf('|', p1 + 1);
    int p3 = cmd.indexOf('|', p2 + 1);
    int p4 = cmd.indexOf('|', p3 + 1);
    if (p1 == -1 || p2 == -1 || p3 == -1 || p4 == -1) return;
    String typeStr = cmd.substring(p1 + 1, p2);
    String durationStr = cmd.substring(p2 + 1, p3);
    String timeStr = cmd.substring(p3 + 1, p4);
    String messageStr = cmd.substring(p4 + 1);

    int hour = timeStr.substring(0,2).toInt();
    int minute = timeStr.substring(3,5).toInt();

    AlertItem item;

    if (typeStr == "WATER") item.type = ALERT_WATER;
    else if (typeStr == "MEDICINE") item.type = ALERT_MEDICINE;
    else if (typeStr == "SLEEP") item.type = ALERT_SLEEP;
    else if (typeStr == "APPOINTMENT") item.type = ALERT_APPOINTMENT;
    else item.type = ALERT_CUSTOM;

    item.message = messageStr;
    item.duration = durationStr.toInt();
    scheduleAlert(item, hour, minute);
  }

  if (cmd.startsWith("TIME|")) {
    String payload = cmd.substring(5);
    int y,m,d,hh,mm;
    if (parseTimePayload(payload, y,m,d,hh,mm)) {
      applyTime(y,m,d,hh,mm);
      uiNeedsRefresh = true;
    }
    return;
  }
}

// ==========================
// BLE CALLBACKS
// ==========================
void drawCurrentUI();

class HAWKServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *server) override {
    bleConnected = true;
    dados.ble = true;
    dados.aparelho = "Celular conectado";
    dados.sync = "Aguardando sync";
    drawCurrentUI();

    BLEDescriptor* desc = pStatusChar->getDescriptorByUUID(BLEUUID((uint16_t)0x2902));
    if (desc != nullptr) {
      uint8_t notifyOn[] = {0x01, 0x00};
      desc->setValue(notifyOn, 2);
    }
    uiNeedsRefresh = true;
  }

  void onDisconnect(BLEServer *server) override {
    bleConnected = false;
    dados.ble = false;
    dados.aparelho = "Aguardando";
    dados.sync = "Nao sincronizado";
    BLEDevice::startAdvertising();
    drawCurrentUI();
  }
};
class SyncCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) override {
    String incoming = pCharacteristic->getValue().c_str();
    incoming.trim();
    if (incoming.length() == 0) return;

    updateDateFromSyncString(incoming);
    drawCurrentUI();
  }
};

class CmdCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) override {
    String cmd = pCharacteristic->getValue().c_str();
    cmd.trim();
    if (cmd.length() == 0) return;

    handleBLECommand(cmd);
    drawCurrentUI();
  }
};

void setupBLE() {
  BLEDevice::init(DEVICE_NAME);

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new HAWKServerCallbacks());
  pService = pServer->createService(SERVICE_UUID);

  pStatusChar = pService->createCharacteristic(
    STATUS_UUID,
    BLECharacteristic::PROPERTY_READ |
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pStatusChar->addDescriptor(new BLE2902());

  pSyncChar = pService->createCharacteristic(
    SYNC_UUID,
    BLECharacteristic::PROPERTY_WRITE
  );
  pSyncChar->setCallbacks(new SyncCallbacks());
  pCmdChar = pService->createCharacteristic(
    CMD_UUID,
    BLECharacteristic::PROPERTY_WRITE
  );
  pCmdChar->setCallbacks(new CmdCallbacks());

  pStatusChar->setValue("HAWK iniciado");
  pService->start();
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  BLEAdvertisementData adv;
  adv.setName(DEVICE_NAME);
  adv.setCompleteServices(BLEUUID(SERVICE_UUID));

  pAdvertising->setAdvertisementData(adv);
  pAdvertising->setScanResponse(true);

  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);

  BLEDevice::startAdvertising();

  dados.aparelho = "Aguardando";
  dados.sync = "Nao sincronizado";

  pBLEScan = BLEDevice::getScan();
  pBLEScan->setActiveScan(true);
  pBLEScan->setInterval(100);
  pBLEScan->setWindow(80);
}

void sendBLEStatus() {
  if (!bleConnected || pStatusChar == nullptr) return;

  String payload = makeStatusPayload();
  pStatusChar->setValue(payload.c_str());
  pStatusChar->notify();
}

// ==========================
// BOOT
// ==========================
void drawBootScreen() {
  gfx->fillScreen(BG_COLOR);
  card(70, 45, 180, 72, CARD_2);

  textCenter(160, 55, "HAWK", TXT_CYAN2, 3);
  textCenter(160, 85, "Projeto de TCC - ECA10 2026.1", TXT_WHITE, 1);
  textCenter(160, 105, "Inicializando...", TXT_SOFT, 1);

  delay(900);
}

// ==========================
// HOME
// ==========================
void drawHome() {
  gfx->fillScreen(BG_COLOR);
  drawTopBar();

  card(10, 34, 146, 108, CARD_1);

  textCenter(83, 52, dados.hora, TXT_WHITE, 3);
  textCenter(83, 84, dados.data, TXT_SOFT, 1);

  card(24, 108, 118, 24, CARD_2);
  text(34, 117, "Segure < para menu", TXT_SOFT, 1);
  card(164, 34, 68, 50, CARD_2);
  card(242, 34, 68, 50, CARD_2);
  card(164, 92, 68, 50, CARD_2);
  card(242, 92, 68, 50, CARD_2);
  text(174, 46, "BPM", TXT_CYAN, 1);
  text(252, 46, "SpO2", TXT_CYAN, 1);
  text(174, 104, "Passos", TXT_CYAN, 1);
  text(252, 104, "BLE", TXT_CYAN, 1);
  text(174, 63, dados.dedo && dados.bpm > 0 ? String(dados.bpm) : "--", TXT_WHITE, 2);
  text(252, 63, dados.dedo && dados.spo2 > 0 ? String(dados.spo2) + "%" : "--", TXT_WHITE, 2);
  text(174, 120, String(dados.passos), TXT_WHITE, 1);
  text(252, 120, dados.ble ? "ON" : "OFF", dados.ble ? OK_GREEN : WARN_ORANGE, 1);

  drawFooter("<", ">");
}

// ==========================
// SAUDE
// ==========================
void drawHealth() {
  gfx->fillScreen(BG_COLOR);
  drawTopBar();
  drawSectionTitle("SAUDE");
  drawFooterCenter(maxOK ? "MAX30102 ativo" : "MAX30102 nao detectado");

  updateHealthDynamic();
}

void updateHealthDynamic() {
  if (currentScreen == SCREEN_HOME) {
    gfx->fillRect(12, 44, 142, 50, CARD_1);
    textCenter(83, 52, dados.hora, TXT_WHITE, 3);
    textCenter(83, 84, dados.data, TXT_SOFT, 1);

    gfx->fillRect(170, 58, 56, 22, CARD_2);
    text(174, 63, dados.dedo && dados.bpm > 0 ? String(dados.bpm) : "--", TXT_WHITE, 2);
    gfx->fillRect(248, 58, 56, 22, CARD_2);
    text(252, 63, dados.dedo && dados.spo2 > 0 ? String(dados.spo2) + "%" : "--", TXT_WHITE, 2);
    gfx->fillRect(170, 114, 56, 16, CARD_2);
    text(174, 120, String(dados.passos), TXT_WHITE, 1);
    gfx->fillRect(248, 114, 56, 16, CARD_2);
    text(252, 120, dados.ble ? "ON" : "OFF", dados.ble ? OK_GREEN : WARN_ORANGE, 1);
    return;
  }

  if (currentScreen == SCREEN_HEALTH) {

    card(10, 46, 146, 60, CARD_1);
    text(18, 54, "Freq. cardiaca", TXT_CYAN, 1);
    textCenter(83, 74, (dados.dedo && dados.bpm > 0) ? String(dados.bpm) : "--", TXT_WHITE, 3);
    textCenter(83, 98, "bpm", TXT_SOFT, 1);

    card(164, 46, 146, 60, CARD_1);
    text(172, 54, "Oxigenacao", TXT_CYAN, 1);
    textCenter(237, 74, (dados.dedo && dados.spo2 > 0) ? String(dados.spo2) + "%" : "--", TXT_WHITE, 3);
    textCenter(237, 98, "SpO2", TXT_SOFT, 1);

    card(10, 114, 300, 28, CARD_2);
    text(18, 124, "Leitura", TXT_CYAN, 1);
    drawPill(78, 120, dados.dedo ? "OK" : "SEM DEDO", dados.dedo ? OK_GREEN : WARN_ORANGE, BG_COLOR);
  }
}

// ==========================
// MOVIMENTO
// ==========================
void drawMotionStatic() {
  gfx->fillScreen(BG_COLOR);
  drawTopBar();
  drawSectionTitle("MOVIMENTO");

  card(10, 46, 146, 46, CARD_1);
  card(164, 46, 146, 46, CARD_1);
  card(10, 100, 300, 30, CARD_2);

  drawFooterCenter(mpuOK ? "MPU6050 ativo" : "MPU6050 nao detectado");
}

void updateMotionDynamic() {
  card(10, 46, 146, 46, CARD_1);
  text(18, 50, "Passos", TXT_CYAN, 1); 
  text(18, 64, String(dados.passos), TXT_WHITE, 2);

  card(164, 46, 146, 46, CARD_1);
  text(172, 50, "Temp MPU", TXT_CYAN, 1);
  text(172, 64, String(dados.tempMPU, 1) + "C", TXT_WHITE, 2); 

  card(10, 100, 300, 30, CARD_2);
  text(18, 108, "ACCEL", TXT_CYAN, 1);
  String accelLine = "X:" + String(dados.ax, 2) + "  Y:" + String(dados.ay, 2) + "  Z:" + String(dados.az, 2);
  text(78, 108, accelLine, TXT_WHITE, 1);
}

void drawMotion() {
  drawMotionStatic();
  updateMotionDynamic();
}

// ==========================
// CONEXAO
// ==========================
void drawConnection() {
  gfx->fillScreen(BG_COLOR);
  drawTopBar();
  drawSectionTitle("CONEXAO");

 
  card(10, 46, 300, 100, CARD_1); 

  text(18, 76, "Bluetooth", TXT_CYAN, 1);
  text(18, 96, dados.ble ? "Conectado" : "Offline", dados.ble ? OK_GREEN : WARN_ORANGE, 2);

  text(172, 76, "Sync", TXT_CYAN, 1);
  if (dados.sync.length() > 18) {
    text(172, 96, "Nao sincronizado", TXT_WHITE, 1);
  } else {
    text(172, 96, dados.sync, TXT_WHITE, 1);
  }
}


// ==========================
// MENU
// ==========================
void drawMenu() {
  gfx->fillScreen(BG_COLOR);
  drawTopBar();
  drawSectionTitle("MENU");
  for (int i = 0; i < menuCount; i++) {
    int y = 38 + i * 21;
    uint16_t color = (i == menuIndex) ? MENU_HL : CARD_2;
    card(72, y, 176, 17, color);
    text(86, y + 5, String(menuItems[i]), (i == menuIndex) ? BG_COLOR : TXT_WHITE, 1);
  }

  drawFooter("<", ">");
}

// ==========================
// BRILHO
// ==========================
void drawBrightnessScreen() {
  gfx->fillScreen(BG_COLOR);
  drawTopBar();
  drawSectionTitle("BRILHO");

  card(10, 46, 146, 40, CARD_1);
  card(164, 46, 146, 40, CARD_2);
  String pct = String((brightnessLevels[brightnessIndex] * 100) / 255) + "%";

  text(18, 58, "Nivel atual", TXT_CYAN, 1);
  textCenter(83, 70, pct, TXT_WHITE, 2);

  drawMiniBar(172, 62, 96, 10, brightnessLevels[brightnessIndex], 255, OK_GREEN);
  text(172, 88, "Segure qualquer botao", TXT_SOFT, 1);
  text(172, 100, "para voltar", TXT_SOFT, 1);

  drawFooter("<", ">");
}

// ==========================
// MEDICAO
// ==========================
void drawMeasureScreen() {
  gfx->fillScreen(BG_COLOR);
  drawTopBar();
  drawSectionTitle("MEDICAO");
  card(10, 46, 146, 34, CARD_1);
  card(164, 46, 146, 34, CARD_1);
  card(10, 88, 300, 34, CARD_2);
  text(18, 58, "BPM ao vivo", TXT_CYAN, 1);
  text(18, 74, (dados.dedo && dados.bpm > 0) ? String(dados.bpm) : "--", TXT_WHITE, 2);
  text(172, 58, "SpO2 ao vivo", TXT_CYAN, 1);
  text(172, 74, (dados.dedo && dados.spo2 > 0) ? String(dados.spo2) + "%" : "--", TXT_WHITE, 2);
  text(18, 100, "Status", TXT_CYAN, 1);
  drawPill(78, 103, dados.dedo ? "LENDO" : "SEM DEDO", dados.dedo ? OK_GREEN : WARN_ORANGE, BG_COLOR);
  drawFooterCenter("Use < para sair");
}

// ==========================
// ALERTA
// ==========================

uint16_t alertBgColorByType(AlertType type) {
  switch (type) {
    case ALERT_WATER:     return 0x001F;
    case ALERT_MEDICINE:  return 0x0320;
    case ALERT_SLEEP:     return 0x780F;
    case ALERT_NONE:      return 0xFFE0;
    case ALERT_CUSTOM:    return 0xFFE0;
    default:              return 0xF800;
  }
}

uint16_t alertTextColorByBg(uint16_t bg) {
  if (bg == 0xFFE0) return 0x0000;
  return 0xFFFF; // branco
}
void drawFallAlert() {
  uint16_t bg = 0xF800;
  uint16_t fg = alertTextColorByBg(bg); 

  gfx->fillScreen(bg);
  textCenter(160, 54,  "ALERTA",          fg, 3);
  textCenter(160, 86,  "POSSIVEL QUEDA",  fg, 2);
  textCenter(160, 116, "Pressione um botao", fg, 1);
  textCenter(160, 132, "para sair",          fg, 1);
}
void drawRemoteAlert() {
  uint16_t bg = alertBgColorByType(currentAlert.type);
  uint16_t fg = alertTextColorByBg(bg);

  gfx->fillScreen(bg);

  textCenter(160, 30, "ALERTA!", fg, 3);
  textCenter(160, 90, currentAlert.message, fg, 2);
}

// ==========================
// SOBRE
// ==========================
void drawAboutScreen() {
  gfx->fillScreen(BG_COLOR);

  drawTopBar();
  drawSectionTitle("SOBRE");

  card(60, 50, 200, 90, CARD_1);

  textCenter(160, 56, "HAWK", TXT_CYAN2, 2);
  textCenter(160, 78, "Prototipo V3", TXT_WHITE, 1);
  textCenter(160, 94, "ESP32-S3", TXT_SOFT, 1);
  textCenter(160, 110, "MPU6050 + MAX30102 + BLE", TXT_SOFT, 1);
  textCenter(160, 126, "Projeto de TCC - ECA10 2026.1", TXT_SOFT, 1);

  drawFooter("<", ">");
}

// ==========================
// RENDER
// ==========================
void drawWatchScreen() {
  switch (currentScreen) {
    case SCREEN_HOME:       drawHome(); break;
    case SCREEN_HEALTH:     drawHealth(); break;
    case SCREEN_MOTION:     drawMotion(); break;
    case SCREEN_CONNECTION: drawConnection(); break;
  }
}

void drawCurrentUI() {
  switch (appState) {
    case STATE_WATCH:       drawWatchScreen(); break;
    case STATE_MENU:        drawMenu(); break;
    case STATE_BRIGHTNESS:  drawBrightnessScreen(); break;
    case STATE_MEASURE:     drawMeasureScreen(); break;
    case STATE_ALERT_FALL:  drawFallAlert(); break;
    case STATE_ABOUT:       drawAboutScreen(); break;
    case STATE_REMOTE_ALERT:       drawRemoteAlert(); break;
  }
  resetText();
}

// ==========================
// DADOS GERAIS
// ==========================
void updateGeneralData() {
  if (!timeSynced) return;
  if (currentHour == 0 && currentMinute == 0) {
    for (int i = 0; i < scheduledCount; i++) {
      scheduledAlerts[i].fired = false;
    }
  }

  if (millis() - lastTimeUpdate >= 60000) {
    lastTimeUpdate += 60000;

    currentMinute++;
    if (currentMinute >= 60) {
      currentMinute = 0;
      currentHour++;
      if (currentHour >= 24) {
            currentHour = 0;
            currentDay++;
            int daysInMonth = 31;

            if (currentMonth == 4 || currentMonth == 6 || 
                currentMonth == 9 || currentMonth == 11)
                daysInMonth = 30;
            if (currentMonth == 2) {
                bool leap = (currentYear % 4 == 0 && 
                            (currentYear % 100 != 0 || currentYear % 400 == 0));
                daysInMonth = leap ? 29 : 28;
            }

            if (currentDay > daysInMonth) {
                currentDay = 1;
                currentMonth++;

                if (currentMonth > 12) {
                    currentMonth = 1;
                    currentYear++;
                }
            }

            char dataBuffer[11];
            sprintf(dataBuffer, "%02d/%02d/%04d",
                    currentDay, currentMonth, currentYear);
            dados.data = String(dataBuffer);
          }
    }

    char horaBuffer[6];
    sprintf(horaBuffer, "%02d:%02d", currentHour, currentMinute);
    dados.hora = String(horaBuffer);
  }
}

// ==========================
// MENU ACTION
// ==========================
void executeMenuItem() {
  switch (menuIndex) {
    case 0: appState = STATE_WATCH; break;
    case 1: appState = STATE_BRIGHTNESS; break;
    case 2: appState = STATE_MEASURE; break;
    case 3:
      dados.queda = true;
      fallUntil = millis() + 3500;
      appState = STATE_ALERT_FALL;
      break;
    case 4: appState = STATE_ABOUT; break;
  }
  drawCurrentUI();
}

// ==========================
// BUTTONS
// ==========================
void handleButtons() {
  bool leftState = digitalRead(BTN_LEFT);
  bool rightState = digitalRead(BTN_RIGHT);

  if (lastLeftState == HIGH && leftState == LOW) {
    leftPressStart = millis();
    leftLongHandled = false;
  }

  if (lastRightState == HIGH && rightState == LOW) {
    rightPressStart = millis();
    rightLongHandled = false;
  }

  if (leftState == LOW && !leftLongHandled && (millis() - leftPressStart > longPressTime)) {
    leftLongHandled = true;
    if (appState == STATE_WATCH && currentScreen == SCREEN_HOME) {
      appState = STATE_MENU;
      drawCurrentUI();
    } else if (appState == STATE_BRIGHTNESS) {
      appState = STATE_MENU;
      drawCurrentUI();
    }
  }

  if (rightState == LOW && !rightLongHandled && (millis() - rightPressStart > longPressTime)) {
    rightLongHandled = true;
    if (appState == STATE_WATCH && currentScreen == SCREEN_HOME) {
      appState = STATE_MEASURE;
      drawCurrentUI();
    } else if (appState == STATE_BRIGHTNESS) {
      appState = STATE_MENU;
      drawCurrentUI();
    }
  }

  if (millis() - lastDebounce > debounceDelay) {
    if (lastLeftState == LOW && leftState == HIGH && !leftLongHandled) {
      switch (appState) {
        case STATE_WATCH:
          transitionFlash();
          if (currentScreen == SCREEN_HOME) currentScreen = SCREEN_CONNECTION;
          else currentScreen = (Screen)((int)currentScreen - 1);
          break;
        case STATE_MENU:
          if (menuIndex == 0) menuIndex = menuCount - 1;
          else menuIndex--;
          break;

        case STATE_BRIGHTNESS:
          if (brightnessIndex > 0) brightnessIndex--;
          applyBrightness();
          break;

        case STATE_MEASURE:
          appState = STATE_WATCH;
          break;
        case STATE_ALERT_FALL:
          dados.queda = false;
          appState = STATE_WATCH;
          break;
        case STATE_ABOUT:
          appState = STATE_MENU;
          break;
        case STATE_REMOTE_ALERT:
          remoteAlertActive = false;
          appState = STATE_WATCH;
          break;
      }
      drawCurrentUI();
      lastDebounce = millis();
    }

    if (lastRightState == LOW && rightState == HIGH && !rightLongHandled) {
      switch (appState) {
        case STATE_WATCH:
          transitionFlash();
          if (currentScreen == SCREEN_CONNECTION) currentScreen = SCREEN_HOME;
          else currentScreen = (Screen)((int)currentScreen + 1);
          break;
        case STATE_MENU:
          executeMenuItem();
          break;
        case STATE_BRIGHTNESS:
          if (brightnessIndex < 3) brightnessIndex++;
          applyBrightness();
          break;
        case STATE_MEASURE:
          drawCurrentUI();
          break;
        case STATE_ALERT_FALL:
          dados.queda = false;
          appState = STATE_WATCH;
          break;
        case STATE_ABOUT:
          appState = STATE_MENU;
          break;
        case STATE_REMOTE_ALERT:
          remoteAlertActive = false;
          appState = STATE_WATCH;
          break;
        }
      drawCurrentUI();
      lastDebounce = millis();
    }
  }

  lastLeftState = leftState;
  lastRightState = rightState;
}

void processScheduledAlerts() {
  if (!timeSynced) return;

  for (int i = 0; i < scheduledCount; i++) {
    if (!scheduledAlerts[i].valid) continue;
    if (scheduledAlerts[i].hour == currentHour &&
        scheduledAlerts[i].minute == currentMinute) {

      if (!scheduledAlerts[i].fired) {
        scheduledAlerts[i].fired = true;
        triggerLocalAlert(
          scheduledAlerts[i].alert.type,
          scheduledAlerts[i].alert.duration,
          scheduledAlerts[i].alert.message
        );
      }
    } else {
      scheduledAlerts[i].fired = false;
    }
  }
} 

  void applyTime(int y, int m, int d, int hh, int mm) {
    currentYear = y;
    currentMonth = m;
    currentDay = d;
    currentHour = hh;
    currentMinute = mm;

    timeSynced = true;
    lastTimeUpdate = millis();

    char bufH[6];
    snprintf(bufH, sizeof(bufH), "%02d:%02d", currentHour, currentMinute);
    dados.hora = String(bufH);

    char bufD[11];
    snprintf(bufD, sizeof(bufD), "%02d/%02d/%04d", currentDay, currentMonth, currentYear);
    dados.data = String(bufD);
  }
// ==========================
// SETUP
// ==========================

void setup() {
  Serial.begin(115200);

  pinMode(LCD_POWER, OUTPUT);
  digitalWrite(LCD_POWER, HIGH);

  pinMode(BTN_LEFT, INPUT_PULLUP);
  pinMode(BTN_RIGHT, INPUT_PULLUP);
  if (!ledcAttach(GFX_BL, 5000, 8)) {
  }
  applyBrightness();

  if (!gfx->begin()) {
    while (1);
  }

  Wire.begin(I2C_SDA, I2C_SCL);
  Wire.setClock(400000);
  randomSeed(millis());

  mpuOK = initMPU6050();
  maxOK = initMAX30102();

  setupBLE();

  drawBootScreen();
  drawCurrentUI();
}

// ==========================
// LOOP
// ==========================
void loop() {
  updateRtcFromMillis();
  updateGeneralData();
  
  // Funções dos sensores (Agora rodando na mesma fluidez do Arquivo 2)
  updateMPUData();
  updateMAX30102();
  
  handleButtons();

  if (uiNeedsRefresh) {
    uiNeedsRefresh = false;
    drawCurrentUI();
  }

  if (dados.queda && appState == STATE_WATCH) {
    appState = STATE_ALERT_FALL;
    drawCurrentUI();
  }

  // ==========================
  // GERENCIADOR DE ALERTA REMOTO
  // ==========================
  if (!remoteAlertActive && hasPendingAlert()) {
    if (dequeueAlert(currentAlert)) {
      remoteAlertActive = true;
      remoteAlertUntil = millis() + currentAlert.duration;
      appState = STATE_REMOTE_ALERT;
      drawCurrentUI();
    }
  }
  processScheduledAlerts();
  
  if (remoteAlertActive && millis() > remoteAlertUntil) {
    remoteAlertActive = false;
    appState = STATE_WATCH;
    drawCurrentUI();
  }

  if (bleConnected && millis() - lastBLESend > 200) {
    lastBLESend = millis();
    sendBLEStatus();
  }

  static unsigned long lastMotionUI = 0;
  if (appState == STATE_WATCH && currentScreen == SCREEN_MOTION && millis() - lastMotionUI > 120) {
    lastMotionUI = millis();
    updateMotionDynamic();
  }

  static unsigned long lastHealthUI = 0;
  if (appState == STATE_WATCH &&
      (currentScreen == SCREEN_HOME || currentScreen == SCREEN_HEALTH) &&
      millis() - lastHealthUI > 200) {
    lastHealthUI = millis();
    updateHealthDynamic(); // ✅ sem fillScreen
  }
}
