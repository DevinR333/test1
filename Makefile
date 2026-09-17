PYTHON  ?= python3
ROM     ?= tests/fixture.gb
SYMBOLS ?=
OUT     ?= build/src
CFLAGS  ?= -O2 -Wall -Wextra -Iruntime -I$(OUT)

RECOMP_ARGS = $(ROM) -o $(OUT)
ifneq ($(SYMBOLS),)
RECOMP_ARGS += --symbols $(SYMBOLS)
endif

.PHONY: all fixture recompile report check test-present clean

all: check

fixture:
	$(PYTHON) tests/make_fixture.py tests/fixture.gb

recompile: | $(OUT)
	$(PYTHON) tools/gbrecomp.py $(RECOMP_ARGS)

report:
	$(PYTHON) tools/gbrecomp.py $(ROM) --report-only

# Verifies the emitter produces C that actually compiles.
check: fixture recompile test-present
	cp runtime/gb.h $(OUT)/
	$(CC) -fsyntax-only $(CFLAGS) $(OUT)/*.c
	$(CC) -fsyntax-only $(CFLAGS) runtime/alu.c runtime/memory.c
	@echo "generated C and runtime compile clean"

test-present: | $(OUT)
	$(CC) -O2 -Wall -Wextra -Iruntime -o $(OUT)/test_present \
		tests/test_present.c runtime/present.c -lm
	$(OUT)/test_present

$(OUT):
	mkdir -p $(OUT)

clean:
	rm -rf build tests/fixture.gb
