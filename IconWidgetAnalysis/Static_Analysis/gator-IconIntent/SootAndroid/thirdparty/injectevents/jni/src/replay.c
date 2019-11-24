#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h> 
#include <unistd.h>
#include <linux/input.h>
#include <linux/time.h>

#include "uinput.h"

const char *EV_PREFIX  = "/dev/input/";
//const char *IN_FN = "/data/presto/events";

/* NB event4 is the compass -- not required for tests. */
//char *ev_devices[] = {"event0", "event1", "event2", "event3" /*, "event4" */};
char *ev_devices[] = {"event0"};
#define NUM_DEVICES (sizeof(ev_devices) / sizeof(char *))

int out_fds[NUM_DEVICES];
int num_events;
int in_fd;

int
init(const char *IN_FN)
{
	char buf[256];
	int i;
	struct stat statinfo;

	for(i = 0; i < NUM_DEVICES; i++) {
		sprintf(buf, "%s%s", EV_PREFIX, ev_devices[i]);
		out_fds[i] = open(buf, O_WRONLY | O_NDELAY);
		if (out_fds[i] < 0) {
			printf("Couldn't open output device\n");
			return 1;
		}
	}

	if(stat(IN_FN, &statinfo) == -1) {
		printf("Couldn't stat input\n");
		return 2;
	}

	num_events = statinfo.st_size / (sizeof(struct input_event) + sizeof(int));

	if((in_fd = open(IN_FN, O_RDONLY)) < 0) {
		printf("Couldn't open input\n");
		return 3;
	}

	// Hacky ioctl init
	ioctl (out_fds[3], UI_SET_EVBIT, EV_KEY);
	ioctl (out_fds[3], UI_SET_EVBIT, EV_REP);
	ioctl (out_fds[1], UI_SET_EVBIT, EV_ABS);

	return 0;
}

int
replay()
{
	struct timeval tdiff;
	struct input_event event;
	int i, outputdev;

	timerclear(&tdiff);
	
	for(i = 0; i < num_events; i++) {
		struct timeval now, tevent, tsleep;

		if(read(in_fd, &outputdev, sizeof(outputdev)) != sizeof(outputdev)) {
			printf("Input read error\n");
			return 1;
		}

		if(read(in_fd, &event, sizeof(event)) != sizeof(event)) {
			printf("Input read error\n");
			return 2;
		}

		gettimeofday(&now, NULL);
		if (!timerisset(&tdiff)) {
			timersub(&now, &event.time, &tdiff);
		}

		timeradd(&event.time, &tdiff, &tevent);
		timersub(&tevent, &now, &tsleep);
		if (tsleep.tv_sec > 0 || tsleep.tv_usec > 100)
			select(0, NULL, NULL, NULL, &tsleep);

		event.time = tevent;

		if(write(out_fds[outputdev], &event, sizeof(event)) != sizeof(event)) {
			printf("Output write error\n");
			return 2;
		}

//		printf("input %d, time %ld.%06ld, type %d, code %d, value %d\n", outputdev,
//				event.time.tv_sec, event.time.tv_usec, event.type, event.code, event.value);
	}

	return 0;
}

int main(int argc, const char* argv[])
{
  if (argc != 2) {
    printf("Usage: %s RecordFileName\n", argv[0]);
  }
	if(init(argv[1]) != 0) {
		printf("init failed\n");
		return 1;
	}

	if(replay() != 0) {
		printf("replay failed\n");
		return 2;
	}
}

