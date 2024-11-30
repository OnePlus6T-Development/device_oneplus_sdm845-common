# Board specific SELinux policy variable definitions
SEPOLICY_PATH:= device/oneplus/sdm845-common/sepolicy/sepolicy_vndr
QSSI_SEPOLICY_PATH:= device/qcom/sepolicy
SYS_ATTR_PROJECT_PATH := $(TOP)/device/qcom/sepolicy/generic/public/attribute
SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS := \
    $(SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS) \
    $(QSSI_SEPOLICY_PATH)/generic/public \
    $(QSSI_SEPOLICY_PATH)/generic/public/attribute

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS := \
    $(SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS) \
    $(QSSI_SEPOLICY_PATH)/generic/private

SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS := \
    $(SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS) \
    $(QSSI_SEPOLICY_PATH)/qva/public \
    $(QSSI_SEPOLICY_PATH)/qva/public/attribute

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS := \
    $(SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS) \
    $(QSSI_SEPOLICY_PATH)/qva/private

#once all the services are moved to Product /ODM above lines will be removed.
# sepolicy rules for product images
PRODUCT_PUBLIC_SEPOLICY_DIRS := \
    $(PRODUCT_PUBLIC_SEPOLICY_DIRS) \
    $(QSSI_SEPOLICY_PATH)/generic/product/public \
    $(QSSI_SEPOLICY_PATH)/qva/product/public

PRODUCT_PRIVATE_SEPOLICY_DIRS := \
    $(PRODUCT_PRIVATE_SEPOLICY_DIRS) \
    $(QSSI_SEPOLICY_PATH)/generic/product/private \
    $(QSSI_SEPOLICY_PATH)/qva/product/private

ifeq (,$(filter sdm710, $(TARGET_BOARD_PLATFORM)))
    BOARD_VENDOR_SEPOLICY_DIRS := \
       $(BOARD_VENDOR_SEPOLICY_DIRS) \
       $(SEPOLICY_PATH) \
       $(SEPOLICY_PATH)/generic/vendor/common \
       $(SEPOLICY_PATH)/generic/vendor/common/attribute \
       $(SEPOLICY_PATH)/qva/vendor/ssg \
       $(SEPOLICY_PATH)/qva/vendor/common

    ifeq ($(TARGET_SEPOLICY_DIR),)
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/generic/vendor/$(TARGET_BOARD_PLATFORM)
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/qva/vendor/$(TARGET_BOARD_PLATFORM)
    else
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/generic/vendor/$(TARGET_SEPOLICY_DIR)
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/qva/vendor/$(TARGET_SEPOLICY_DIR)
    endif

    ifeq ($(TARGET_BOARD_PLATFORM),bengal)
      ifeq ($(BOARD_USES_LEGACY_IMS_SEPOLICY),true)
        BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/qva/vendor/bengal/legacy-ims
      else
        BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/qva/vendor/bengal/ims
      endif
    endif

    ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/generic/vendor/test
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/qva/vendor/test
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/qva/vendor/test/sysmonapp
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/qva/vendor/test/mst_test_app
    endif
endif

ifneq (,$(filter sdm710, $(TARGET_BOARD_PLATFORM)))
    BOARD_VENDOR_SEPOLICY_DIRS := \
                 $(BOARD_VENDOR_SEPOLICY_DIRS) \
                 $(SEPOLICY_PATH) \
                 $(SEPOLICY_PATH)/legacy/vendor/ssg \
                 $(SEPOLICY_PATH)/legacy/vendor/common

    ifeq ($(TARGET_USES_LOGDUMP_AS_METADATA),true)
        BOARD_SEPOLICY_M4DEFS += logdump_partition=metadata_block_device
    else
        BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/legacy/vendor/common/logdump
    endif

    ifeq ($(TARGET_SEPOLICY_DIR),)
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/legacy/vendor/$(TARGET_BOARD_PLATFORM)
    else
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/legacy/vendor/$(TARGET_SEPOLICY_DIR)
    endif
    ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
      ifneq ($(PRODUCT_SET_DEBUGFS_RESTRICTIONS),true)
        BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/legacy/vendor/common/debugfs
        BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/legacy/vendor/test/debugfs
      endif
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/legacy/vendor/test
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/legacy/vendor/test/sysmonapp
      BOARD_VENDOR_SEPOLICY_DIRS += $(SEPOLICY_PATH)/legacy/vendor/test/mst_test_app
    endif
endif