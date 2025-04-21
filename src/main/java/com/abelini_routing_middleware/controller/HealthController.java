package com.abelini_routing_middleware.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/")
public class HealthController {

    private final ApplicationContext applicationContext;

    @Autowired
    public HealthController(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @GetMapping("/health")
    public String health(Model model) throws InterruptedException {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        OperatingSystem os = systemInfo.getOperatingSystem();

        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();

        CentralProcessor processor = hal.getProcessor();
        int pid = os.getProcessId();

        // Initial system and process snapshot
        long[] prevSystemCpuTicks = processor.getSystemCpuLoadTicks();
        OSProcess prevProcess = os.getProcess(pid);

        Thread.sleep(500);

        double appCpuLoad = os.getProcess(pid).getProcessCpuLoadBetweenTicks(prevProcess) * 100.0 / processor.getLogicalProcessorCount();
        double systemCpuLoad = processor.getSystemCpuLoadBetweenTicks(prevSystemCpuTicks) * 100.0;

        GlobalMemory memory = hal.getMemory();
        long totalMem = memory.getTotal();
        long availMem = memory.getAvailable();
        long usedMem = totalMem - availMem;

        List<Map<String, Object>> disks = new ArrayList<>();
        for (OSFileStore fs : os.getFileSystem().getFileStores()) {
            Map<String, Object> disk = new HashMap<>();
            disk.put("name", fs.getMount());
            disk.put("type", fs.getType());
            disk.put("total", fs.getTotalSpace() / (1024 * 1024));
            disk.put("usable", fs.getUsableSpace() / (1024 * 1024));
            disks.add(disk);
        }

        List<Map<String, Object>> network = new ArrayList<>();
        for (NetworkIF net : hal.getNetworkIFs()) {
            net.updateAttributes();
            Map<String, Object> netInfo = new HashMap<>();
            netInfo.put("name", net.getName());
            netInfo.put("mac", net.getMacaddr());
            netInfo.put("ipv4", Arrays.toString(net.getIPv4addr()));
            netInfo.put("ipv6", Arrays.toString(net.getIPv6addr()));
            netInfo.put("rxBytes", net.getBytesRecv());
            netInfo.put("txBytes", net.getBytesSent());
            network.add(netInfo);
        }

        Map<String, Object> system = new HashMap<>();
        system.put("cpuLoad", String.format("%.2f", systemCpuLoad));
        system.put("availableProcessors", processor.getLogicalProcessorCount());
        system.put("totalMemoryMB", totalMem / (1024 * 1024));
        system.put("usedMemoryMB", usedMem / (1024 * 1024));
        system.put("freeMemoryMB", availMem / (1024 * 1024));
        system.put("javaVersion", System.getProperty("java.version"));
        system.put("jvmUptimeSec", runtimeMxBean.getUptime() / 1000);
        system.put("systemUptimeSec", os.getSystemUptime());

        Map<String, Object> appResource = new HashMap<>();

        OSProcess currentProcess = os.getProcess(os.getProcessId());
        appResource.put("appPid", currentProcess.getProcessID());
//        appResource.put("appName", ManagementFactory.getRuntimeMXBean().getName());
        appResource.put("appUptimeSec", currentProcess.getUpTime() / 1000);

        appResource.put("appCpuLoadPercent", String.format("%.2f", appCpuLoad));
        appResource.put("appMemoryUsedMB", currentProcess.getResidentSetSize() / (1024 * 1024));
        appResource.put("appVirtualMemoryMB", currentProcess.getVirtualSize() / (1024 * 1024));

        appResource.put("appThreadCount", currentProcess.getThreadCount());

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        appResource.put("jvmLiveThreads", threadBean.getThreadCount());
        appResource.put("jvmDaemonThreads", threadBean.getDaemonThreadCount());
        appResource.put("jvmTotalStartedThreads", threadBean.getTotalStartedThreadCount());

        Map<String, Long> threadStates = new HashMap<>();
        for (ThreadInfo info : threadBean.dumpAllThreads(false, false)) {
            String state = info.getThreadState().name();
            threadStates.put(state, threadStates.getOrDefault(state, 0L) + 1);
        }
        appResource.put("jvmThreadStates", threadStates);

        try {
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) applicationContext.getBean("taskExecutor");
            appResource.put("threadPoolActive", executor.getActiveCount());
            appResource.put("threadPoolSize", executor.getPoolSize());
            appResource.put("threadPoolInfo", null);
        } catch (Exception e) {
            appResource.put("threadPoolInfo", "Not available or not configured.");
        }


        model.addAttribute("timestamp", ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm:ss a")));
        model.addAttribute("apiStatus", "UP");
        model.addAttribute("uiStatus", "UP");
        model.addAttribute("system", system);
        model.addAttribute("disks", disks);
        model.addAttribute("network", network);
        model.addAttribute("app", appResource);

        return "health";
    }
}