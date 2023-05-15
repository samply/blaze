## Test Disk I/O Performance

```sh
fio --name=randread --ioengine=libaio --rw=randread --bs=16k --direct=1 --size=10G --numjobs=8 --runtime=120 --group_reporting --time_based --fsync=1000 --iodepth=32 --filename=test.bin
```

Output:

```text
randread: (g=0): rw=randread, bs=(R) 16.0KiB-16.0KiB, (W) 16.0KiB-16.0KiB, (T) 16.0KiB-16.0KiB, ioengine=libaio, iodepth=32
...
fio-3.28
Starting 8 processes
Jobs: 8 (f=8): [r(8)][100.0%][r=974MiB/s][r=62.3k IOPS][eta 00m:00s]
randread: (groupid=0, jobs=8): err= 0: pid=46840: Wed May 10 16:09:42 2023
  read: IOPS=65.8k, BW=1028MiB/s (1078MB/s)(121GiB/120017msec)
    slat (nsec): min=1663, max=534117, avg=7581.99, stdev=2533.63
    clat (usec): min=142, max=258129, avg=3881.27, stdev=4019.48
     lat (usec): min=149, max=258138, avg=3888.93, stdev=4019.47
    clat percentiles (usec):
     |  1.00th=[  400],  5.00th=[  519], 10.00th=[  553], 20.00th=[  824],
     | 30.00th=[  865], 40.00th=[  914], 50.00th=[ 2769], 60.00th=[ 6194],
     | 70.00th=[ 6587], 80.00th=[ 6849], 90.00th=[ 7308], 95.00th=[ 7898],
     | 99.00th=[13566], 99.50th=[15139], 99.90th=[17957], 99.95th=[19530],
     | 99.99th=[30016]
   bw (  MiB/s): min=  531, max= 1181, per=100.00%, avg=1029.56, stdev=11.80, samples=1912
   iops        : min=34014, max=75634, avg=65891.92, stdev=754.93, samples=1912
  lat (usec)   : 250=0.01%, 500=3.39%, 750=12.87%, 1000=28.43%
  lat (msec)   : 2=4.05%, 4=3.01%, 10=46.41%, 20=1.80%, 50=0.03%
  lat (msec)   : 250=0.01%, 500=0.01%
  cpu          : usr=0.88%, sys=7.50%, ctx=4965910, majf=0, minf=1134
  IO depths    : 1=0.1%, 2=0.1%, 4=0.1%, 8=0.1%, 16=0.1%, 32=100.0%, >=64=0.0%
     submit    : 0=0.0%, 4=100.0%, 8=0.0%, 16=0.0%, 32=0.0%, 64=0.0%, >=64=0.0%
     complete  : 0=0.0%, 4=100.0%, 8=0.0%, 16=0.0%, 32=0.1%, 64=0.0%, >=64=0.0%
     issued rwts: total=7899401,0,0,0 short=0,0,0,0 dropped=0,0,0,0
     latency   : target=0, window=0, percentile=100.00%, depth=32

Run status group 0 (all jobs):
   READ: bw=1028MiB/s (1078MB/s), 1028MiB/s-1028MiB/s (1078MB/s-1078MB/s), io=121GiB (129GB), run=120017-120017msec

Disk stats (read/write):
  sda: ios=7891758/0, merge=69/0, ticks=30603746/0, in_queue=30603746, util=99.97%
```
