PYTHON  ?= python3
ROM     ?= tests/fixture.gb
SYMBOLS ?=
OUT     ?= build/src
CFLAGS  ?= -O2 -Wall -Wextra -Iruntime -I$(OUT)

RECOMP_ARGS = $(ROM) -o $(OUT)
ifneq ($(SYMBOLS),)
RECOMP_ARGS += --symbols $(SYMBOLS)
endif

.PHONY: all fixture recompile report check clean

all: check

fixture:
	$(PYTHON) tests/make_fixture.py tests/fixture.gb

recompile: | $(OUT)
	$(PYTHON) tools/gbrecomp.py $(RECOMP_ARGS)

report:
	$(PYTHON) tools/gbrecomp.py $(ROM) --report-only

# Verifies the emitter produces C that actually compiles.
check: fixture recompile
	cp runtime/gb.h $(OUT)/
	$(CC) -fsyntax-only $(CFLAGS) $(OUT)/*.c
	@echo "generated C compiles clean"

$(OUT):
	mkdir -p $(OUT)

clean:
	rm -rf build tests/fixture.gb
