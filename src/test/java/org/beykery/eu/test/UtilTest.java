package org.beykery.eu.test;

import org.beykery.eu.util.EthContractUtil;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.protocol.Web3j;

import java.io.IOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.beykery.eu.util.EthContractUtil.decodeInputData;

public class UtilTest {

    @Test
    void inputDecode() {
        String input = "0xc54f4161000000000000000000000000000000000000000000000000000000000000006000000000000000000000000021c718c22d52d0f3a789b752d4c2fd5908a8a73300000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000003000000000000000000000000941494a56164ea04d79f9867dddb0dd754a625cc000000000000000000000000238396d4d01ba5621e66894a0228f6b3651f156600000000000000000000000021085e9307a7ec5206ca17db95be9eba7c71362e000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000017b05c19432f6c15600000000000000000000000000000000000000000000000000b9fae319107a40380000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000012059500000000000000000000000000000000000000000000000000000000000000070000000000000000000000000000000000000000000000000000262acd20b2a0000000000000000000000000000000000000000000000000000063084b42dd45000000000000000000000000000000000000000000000000000059026b93092b000000000000000000000000000000000000000000000000000011d765ce26c60000000000000000000000000000000000000000000000000000013cf7f1939a00000000000000000000000000000000000000000000000000000fba4446f6210000000000000000000000000000000000000000000000000000000000000001";
        List<TypeReference<?>> temp = Arrays.asList(
                new TypeReference<DynamicArray<Address>>() {
                },
                new TypeReference<Address>() {
                },
                new TypeReference<DynamicArray<Uint256>>() {
                }
        );
        List<Type> ret = decodeInputData(input, temp);
        System.out.println(ret);
    }

    @Test
    void testHarmony() throws IOException {
        String url = "http://135.181.2.20:9500";
        Web3j web3j = EthContractUtil.getWeb3j(url);
        long cid = EthContractUtil.chainId(web3j);
        System.out.println(cid);
    }

    @Test
    void testTime() {
        long t = 1697126400000L;
        System.out.println(new Date(t));
    }

    @Test
    void testMethodId() throws Exception {
        String mid = EthContractUtil.buildMethodId("transfer", Arrays.asList(new Address(EthContractUtil.DEFAULT_FROM), new Uint(BigInteger.valueOf(123))));
        System.out.println(mid);

        mid = EthContractUtil.buildMethodId("transferFrom", Arrays.asList(new Address(EthContractUtil.DEFAULT_FROM), new Address(EthContractUtil.DEFAULT_FROM), new Uint(BigInteger.valueOf(123))));
        System.out.println(mid);

        mid = EthContractUtil.buildMethodId("balanceOf", Arrays.asList(new Address(EthContractUtil.DEFAULT_FROM)));
        System.out.println(mid);

        mid = EthContractUtil.buildMethodId("getL1Confirmations", Arrays.asList(new Bytes32(new byte[32])));
        System.out.println(mid);

        mid = EthContractUtil.buildMethodId("nitroGenesisBlock", Arrays.asList());
        System.out.println(mid);

        mid = EthContractUtil.buildMethodId("findBatchContainingBlock", Arrays.asList(new Uint64(1)));
        System.out.println(mid);

        Web3j web3j = EthContractUtil.getWeb3j("https://arb1.arbitrum.io/rpc");
        Function function = new Function("findBatchContainingBlock", Arrays.asList(new Uint64(233438348L)), Arrays.asList(new TypeReference<Uint64>() {
        }));
        String encodedFunction = FunctionEncoder.encode(function);
        System.out.println(encodedFunction);
        List<Type> ret = EthContractUtil.call(web3j, function, "0x00000000000000000000000000000000000000c8");
        System.out.println( ret);
    }
}
