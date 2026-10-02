CC ?= cc
CFLAGS := -Wall -O2
SRC := $(wildcard src/*.c)
OBJ := $(SRC:.c=.o)

.PHONY: all clean

all: app

app: $(OBJ)
	$(CC) $(CFLAGS) -o $@ $^

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(OBJ) app
