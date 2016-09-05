import static jcuda.driver.JCudaDriver.*;

import jcuda.Pointer;
import jcuda.driver.*;


public class Main {

    static CUdeviceptr SHA256_CTX;
    static int SHA256_CTX_LEN = 64 + 4 + 8 + (8*8);

    final static int BLOCKS = 1;
    final static int THREADS = 4;

    static CUdeviceptr gpuData;

    static Pointer hostResult;
    static CUdeviceptr gpuResult;

    public static void loadFuncs(CUmodule module, CUfunction a, CUfunction b, CUfunction c){
        cuModuleGetFunction(a, module, "sha256_init");
        cuModuleGetFunction(b, module, "sha256_update");
        cuModuleGetFunction(c, module, "sha256_final");
    }
    public static void init(){
        CUdevice device = new CUdevice();
        cuDeviceGet(device, 0);
        CUcontext context = new CUcontext();
        cuCtxCreate(context, 0, device);
    }
    public static void host_struct_to_device(){
        SHA256_CTX = new CUdeviceptr();
        cuMemAlloc(SHA256_CTX, THREADS * SHA256_CTX_LEN);
    }
    public static void deviceInit(byte[] data){
        gpuData = new CUdeviceptr();
        cuMemAlloc(gpuData, THREADS * data.length);
        cuMemcpyHtoD(gpuData, Pointer.to(data), data.length * THREADS);
    }
    public static void initResult(){
        hostResult = Pointer.to(new byte[32 * THREADS]);
        gpuResult = new CUdeviceptr();
        cuMemAlloc(gpuResult, 32 * THREADS);
    }
    public static void main(String[] args){
        JCudaDriver.setExceptionsEnabled(true);
        cuInit(0);

        init();

        byte[] cpuSHA256_CTX = new byte[SHA256_CTX_LEN * THREADS];
             //uchar data[64]; uint datalen; uint bitlen[2]; uint state[8];
        byte[] data = new byte[]{'a', 'b', 'c'};

        initResult();
        deviceInit(data);

        host_struct_to_device();

        CUmodule module = new CUmodule();
        cuModuleLoad(module, "jhash.ptx");

        CUfunction sha256_init = new CUfunction();
        CUfunction sha256_update = new CUfunction();
        CUfunction sha256_final = new CUfunction();

        loadFuncs(module, sha256_init, sha256_update, sha256_final);

        cuLaunchKernel(sha256_init, 1, 1, 1,
                1, 1, 1,
                0, null, Pointer.to(Pointer.to(SHA256_CTX)), null);

        cuMemcpyDtoH(Pointer.to(cpuSHA256_CTX), SHA256_CTX, SHA256_CTX_LEN * THREADS);

        cuLaunchKernel(sha256_update, BLOCKS, 1, 1,
                THREADS, 1, 1,
                0, null, Pointer.to(Pointer.to(SHA256_CTX), CUdeviceptr.to(gpuData), Pointer.to(new int[]{data.length})), null);

        System.out.println("------------------------------------------------");


        cuLaunchKernel(sha256_final, 1, 1, 1,
                1, 1, 1,
                0, null, Pointer.to(Pointer.to(SHA256_CTX), CUdeviceptr.to(gpuResult)), null);

        cuMemcpyDtoH(hostResult, gpuResult, 32 * THREADS);

        for(int i = 0; i < THREADS; ++i){
            for(int a=0; a<32; a++){
                System.out.print(String.format("%02x", hostResult.getByteBuffer(0, 32).array()[a]));
            }
            System.out.println("");
        }

    }
}
