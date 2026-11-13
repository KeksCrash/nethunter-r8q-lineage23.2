#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#define BT_CMD_PWR_CTRL 0xbfad
int main(int argc,char **argv){if(argc!=2)return 1;char *e;long m=strtol(argv[1],&e,10);if(*e||m<0||m>2)return 1;int fd=open("/dev/btpower",O_RDWR);if(fd<0){perror("open");return 1;}int r=ioctl(fd,BT_CMD_PWR_CTRL,(unsigned long)m);if(r<0)perror("ioctl");close(fd);return r<0?1:0;}
