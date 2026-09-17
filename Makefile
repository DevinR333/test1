PYTHON  ?= python3
ROM     ?= tests/fixture.gb
SYMBOLS ?=
OUT     ?= build/src
CFLAGS  ?= -O2 -Wall -Wextra -Iruntime -I$(OUT)

RECOMP_ARGS = $(ROM) -o $(OUT)
ifneq ($(SYMBOLS),)
RECOMP_ARGS += --symbols $(SYMBOLS)
endif

.PHONY: all fixture recompile report check test-present test-tiles interp run clean

all: check

fixture:
	$(PYTHON) tests/make_fixture.py tests/fixture.gb

recompile: | $(OUT)
	$(PYTHON) tools/gbrecomp.py $(RECOMP_ARGS)

report:
	$(PYTHON) tools/gbrecomp.py $(ROM) --report-only

# Verifies the emitter produces C that actually compiles.
check: fixture recompile interp test-present test-tiles run
	cp runtime/gb.h $(OUT)/
	$(CC) -fsyntax-only $(CFLAGS) $(OUT)/*.c
	$(CC) -fsyntax-only $(CFLAGS) runtime/alu.c runtime/memory.c
	@echo "generated C and runtime compile clean"

test-tiles:
	SCRATCH=$(OUT) $(PYTHON) tests/test_tiles.py

test-present: | $(OUT)
	$(CC) -O2 -Wall -Wextra -Iruntime -o $(OUT)/test_present \
		tests/test_present.c runtime/present.c -lm
	$(OUT)/test_present

RUNTIME = runtime/alu.c runtime/memory.c runtime/ppu.c runtime/machine.c \
          runtime/interp.c runtime/interp_gen.c runtime/io_masks.c runtime/diag.c

# The interpreter fallback is generated from the recompiler's own opcode
# tables, so the two paths cannot disagree about an operand or a flag.
interp:
	$(PYTHON) tools/gen_interp.py runtime/interp_gen.c

# Builds the recompiled fixture against the full runtime and runs it.
run: recompile interp
	cp runtime/gb.h $(OUT)/
	$(CC) -O2 -Wall -Wextra -Iruntime -I$(OUT) -o $(OUT)/harness \
		tests/harness.c $(RUNTIME) $(OUT)/bank_*.c $(OUT)/dispatch.c
	$(OUT)/harness tests/fixture.gb 60

$(OUT):
	mkdir -p $(OUT)

clean:
	rm -rf build tests/fixture.gb
