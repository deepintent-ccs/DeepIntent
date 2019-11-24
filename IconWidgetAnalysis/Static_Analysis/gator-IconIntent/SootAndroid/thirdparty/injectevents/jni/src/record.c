#include <stdio.h>
#include <fcntl.h> 
#include <poll.h>
#include <linux/input.h>
#include "uinput.h"

const char *EV_PREFIX  = "/dev/input/";
const char *OUT_FN = "/data/presto/events";
/* const char *OUT_PREFIX = "/sdcard/"; */

/* NB event4 is the compass -- not required for tests. */
//char *ev_devices[] = {"event0", "event1", "event2", "event3" /*, "event4" */};

// TONY: emulator only has event0
char *ev_devices[] = {"event0"};
#define NUM_DEVICES (sizeof(ev_devices) / sizeof(char *))

struct pollfd in_fds[NUM_DEVICES];
/*
int out_fds[NUM_DEVICES];
*/
int out_fd;

int
init()
{
	char buffer[256];
	int i, fd;

	out_fd = open(OUT_FN, O_WRONLY | O_CREAT | O_TRUNC);
	if(out_fd < 0) {
		printf("Couldn't open output file\n");
		return 1;
	}

	for(i = 0; i < NUM_DEVICES; i++) {
		sprintf(buffer, "%s%s", EV_PREFIX, ev_devices[i]);
		in_fds[i].events = POLLIN;
		in_fds[i].fd = open(buffer, O_RDONLY | O_NDELAY);
		if(in_fds[i].fd < 0) {
			printf("Couldn't open input device %s\n", ev_devices[i]);
			return 2;
		}

		#if 0
		sprintf(buffer, "%s%s", OUT_PREFIX, ev_devices[i]);
		out_fds[i] = open(buffer, O_WRONLY | O_CREAT);
		if(out_fds[i] < 0) {
			printf("Couldn't open output file %s\n", ev_devices[i]);
			return 2;
		}
		#endif
	}
	return 0;
}

int
record()
{
	int i, numread;
	struct input_event event;

	while(1) {
		if(poll(in_fds, NUM_DEVICES, -1) < 0) {
			printf("Poll error\n");
			return 1;
		}

		for(i = 0; i < NUM_DEVICES; i++) {
			if(in_fds[i].revents & POLLIN) {
				/* Data available */
				numread = read(in_fds[i].fd, &event, sizeof(event));
				if(numread != sizeof(event)) {
					printf("Read error\n");
					return 2;
				}
				if(write(out_fd, &i, sizeof(i)) != sizeof(i)) {
					printf("Write error\n");
					return 3;
				}
				if(write(out_fd, &event, sizeof(event)) != sizeof(event)) {
					printf("Write error\n");
					return 4;
				}

//				printf("input %d, time %ld.%06ld, type %d, code %d, value %d\n", i,
//						event.time.tv_sec, event.time.tv_usec, event.type, event.code, event.value);
			}
		}
	}
}

int main()
{
	if (init() != 0) {
		printf("Init failed");
		return 1;
	}

	record();
}


