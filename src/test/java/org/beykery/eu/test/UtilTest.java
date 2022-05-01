package org.beykery.eu.test;

import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    public static List<Type> decodeInputData(String inputData, List<TypeReference<?>> outputParameters) {
        List<Type> result = FunctionReturnDecoder.decode(
                inputData.substring(10),
                convert(outputParameters)
        );
        return result;
    }

    public static List<TypeReference<Type>> convert(List<TypeReference<?>> input) {
        List<TypeReference<Type>> result = new ArrayList<>(input.size());
        result.addAll(
                input.stream()
                        .map(typeReference -> (TypeReference<Type>) typeReference)
                        .collect(Collectors.toList()));
        return result;
    }
}