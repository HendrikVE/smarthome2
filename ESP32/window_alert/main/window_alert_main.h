#include <stdbool.h>
#include "driver/gpio.h"
#include "esp_event_loop.h"

#define BOARD_WIFI_MODE_AP CONFIG_ESP_WIFI_MODE_AP //TRUE:AP FALSE:STA
#define BOARD_WIFI_SSID    CONFIG_ESP_WIFI_SSID
#define BOARD_WIFI_PASS    CONFIG_ESP_WIFI_PASSWORD
#define BOARD_MAX_STA_CONN CONFIG_MAX_STA_CONN

#define GPIO_OUTPUT_MAGNETIC_SENSOR 18
#define GPIO_INPUT_MAGNETIC_SENSOR  4

#define ESP_INTR_FLAG_DEFAULT 0

static const char *TAG = "window alert";

void set_gpio_output(int gpio_pin);
void set_gpio_input(int gpio_pin, bool pull_down, bool pull_up, gpio_int_type_t intr_type);
static void IRAM_ATTR gpio_isr_handler(void* arg);
static void gpio_task_example(void* arg);

static esp_err_t event_handler(void *ctx, system_event_t *event);

void wifi_init_softap();
void wifi_init_sta();

void init_magnetic_sensor();
void init_nvs();
void init_wifi();

void app_main();