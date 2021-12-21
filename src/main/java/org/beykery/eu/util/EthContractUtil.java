package org.beykery.eu.util;

import okhttp3.OkHttpClient;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.*;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketService;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Async;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.ConnectException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * eth合约工具
 */
public class EthContractUtil {

    /**
     * default from address for eth call
     */
    public static final String DEFAULT_FROM = "0x8aCc161acB2626505755bBF36184841B8c099806";
    /**
     * 0 address
     */
    public static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    /**
     * 合约函数call
     *
     * @param function        合约方法
     * @param contractAddress 合约地址
     * @return 函数结果
     * @throws Exception
     */
    public static List<Type> call(Web3j web3j, Function function, String contractAddress) throws Exception {
        String encodedFunction = FunctionEncoder.encode(function);
        EthCall response = web3j.ethCall(
                        Transaction.createEthCallTransaction(DEFAULT_FROM, contractAddress, encodedFunction), DefaultBlockParameterName.LATEST)
                .sendAsync().get();
        String res = response.getValue();
        List<Type> ts = FunctionReturnDecoder.decode(res, function.getOutputParameters());
        return ts;
    }

    /**
     * 查看交易状态
     *
     * @param hash
     * @return
     * @throws IOException
     */
    public static TransactionReceipt transactionReceipt(Web3j web3j, String hash) throws IOException {
        EthGetTransactionReceipt receipt = web3j.ethGetTransactionReceipt(hash).send();
        return receipt.getResult();
    }

    /**
     * 求某一高度交易集合
     *
     * @param web3j
     * @param h
     * @return
     */
    public static List<org.web3j.protocol.core.methods.response.Transaction> transactions(Web3j web3j, BigInteger h) throws IOException {
        EthBlock block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(h), true).send();
        EthBlock.Block b = block.getResult();
        List<EthBlock.TransactionResult> ts = b.getTransactions();
        return ts.stream().map(item -> {
            EthBlock.TransactionObject to = (EthBlock.TransactionObject) item;
            return to.get();
        }).collect(Collectors.toList());
    }

    /**
     * 查询合约状态
     * org.web3j.abi.datatypes 中的类型
     *
     * @param contractAddress 合约地址
     * @param funname         方法名
     * @param inputs          输入
     * @param outputs         输出
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static List<Type> call(Web3j web3j, String contractAddress, String funname, List<Type> inputs, List<TypeReference<?>> outputs) throws Exception {
        Function function = new Function(funname, inputs, outputs);
        return call(web3j, function, contractAddress);
    }

    /**
     * balance of eip20 contract
     *
     * @param web3j
     * @param userAddress
     * @param contractAddress
     * @return
     */
    public static BigInteger balanceOf(Web3j web3j, String userAddress, String contractAddress) throws Exception {
        List<Type> ret = call(web3j, contractAddress, "balanceOf", Collections.singletonList(new Address(userAddress)),
                Collections.singletonList(new TypeReference<Uint256>() {
                }));
        if (!ret.isEmpty()) {
            return ((Uint256) ret.get(0)).getValue();
        }
        return null;
    }

    /**
     * 主币余额
     *
     * @param web3j
     * @param userAddress 用户地址（外部地址或合约地址）
     * @return
     */
    public static BigInteger balance(Web3j web3j, String userAddress) throws Exception {
        EthGetBalance b = web3j.ethGetBalance(userAddress, DefaultBlockParameterName.LATEST).send();
        return b.getBalance();
    }

    /**
     * @param web3j
     * @param contractAddress
     * @return
     */
    public static Uint256 totalSupply(Web3j web3j, String contractAddress) throws Exception {
        List<Type> ret = call(web3j, contractAddress, "totalSupply", Collections.EMPTY_LIST,
                Collections.singletonList(new TypeReference<Uint256>() {
                }));
        if (!ret.isEmpty()) {
            return (Uint256) ret.get(0);
        }
        return null;
    }

    /**
     * decimals for eip20 contract
     *
     * @param web3j
     * @param contractAddress
     * @return
     */
    public static BigInteger decimals(Web3j web3j, String contractAddress) throws Exception {
        List<Type> ret = call(web3j, contractAddress, "decimals", Collections.EMPTY_LIST,
                Collections.singletonList(new TypeReference<Uint256>() {
                }));
        if (!ret.isEmpty()) {
            return ((Uint256) ret.get(0)).getValue();
        }
        return null;
    }

    /**
     * balance for eip20 with decimals
     *
     * @param web3j
     * @param userAddress
     * @param contractAddress
     * @return
     */
    public static double balanceOfWithDecimals(Web3j web3j, String userAddress, String contractAddress) throws Exception {
        BigInteger balance = balanceOf(web3j, userAddress, contractAddress);
        if (balance != null) {
            BigInteger decimals = decimals(web3j, contractAddress);
            if (decimals != null) {
                int d = decimals.intValue();
                return numberWithDecimals(balance, d);
            }
        }
        return 0;
    }

    /**
     * name of contract
     *
     * @param web3j
     * @param contractAddress
     * @return
     */
    public static String name(Web3j web3j, String contractAddress) throws Exception {
        List<Type> ret = call(web3j, contractAddress, "name", Collections.EMPTY_LIST,
                Collections.singletonList(new TypeReference<Utf8String>() {
                }));
        if (!ret.isEmpty()) {
            Utf8String name = (Utf8String) ret.get(0);
            return name.getValue();
        }
        return null;
    }

    /**
     * symbols
     *
     * @param web3j
     * @param contractAddress
     * @return
     */
    public static String symbol(Web3j web3j, String contractAddress) throws Exception {
        List<Type> ret = call(web3j, contractAddress, "symbol", Collections.EMPTY_LIST,
                Collections.singletonList(new TypeReference<Utf8String>() {
                }));
        if (!ret.isEmpty()) {
            Utf8String symbol = (Utf8String) ret.get(0);
            return symbol.getValue();
        }
        return null;
    }

    /**
     * allowance
     *
     * @param web3j
     * @param contractAddress
     * @return
     * @throws Exception
     */
    public static BigInteger allowance(Web3j web3j, String contractAddress, String owner, String spender) throws Exception {
        List<Type> input = new ArrayList<>();
        input.add(new Address(owner));
        input.add(new Address(spender));
        List<Type> ret = call(web3j, contractAddress, "allowance", input,
                Collections.singletonList(new TypeReference<Uint>() {
                }));
        if (!ret.isEmpty()) {
            Uint amount = (Uint) ret.get(0);
            return amount.getValue();
        }
        return null;
    }

    /**
     * @param v
     * @param decimals
     * @return
     */
    public static double numberWithDecimals(Uint256 v, int decimals) {
        return numberWithDecimals(v.getValue(), decimals);
    }

    /**
     * @param v
     * @param decimals
     * @return
     */
    public static double numberWithDecimals(BigInteger v, int decimals) {
        BigDecimal bd = BigDecimal.valueOf(10);
        bd = bd.pow(decimals);
        BigDecimal ret = new BigDecimal(v).divide(bd, decimals, BigDecimal.ROUND_DOWN);
        return ret.doubleValue();
    }

    /**
     * batch
     *
     * @param web3j
     * @return
     */
    public static BatchRequest batchRequest(Web3j web3j) {
        return web3j.newBatch();
    }

    /**
     * batch
     *
     * @param web3j
     * @param fs
     */
    public static List<List<Type>> batch(Web3j web3j, String contract, Function... fs) throws Exception {
        List<List<Type>> list = new ArrayList<>();
        BatchRequest batchRequest = web3j.newBatch();
        Stream.of(fs).forEach(item -> {
            String encode = FunctionEncoder.encode(item);
            Request<?, EthCall> call = web3j.ethCall(Transaction.createEthCallTransaction(DEFAULT_FROM, contract, encode), DefaultBlockParameterName.LATEST);
            batchRequest.add(call);
        });
        List<? extends Response<?>> responses = batchRequest.send().getResponses();
        Iterator<? extends Response<?>> it = responses.iterator();
        Stream.of(fs).forEach(item -> {
            Response<?> res = it.next();
            String hex = res.getResult().toString();
            List<Type> ret = FunctionReturnDecoder.decode(hex, item.getOutputParameters());
            list.add(ret);
        });
        return list;
    }

    /**
     * batch call
     *
     * @param web3j
     * @param contract
     * @param calls
     * @return
     */
    public static List<List<Type>> batch(Web3j web3j, String contract, RemoteFunctionCall... calls) throws IOException {
        List<List<Type>> list = new ArrayList<>();
        BatchRequest batchRequest = web3j.newBatch();
        Stream.of(calls).forEach(item -> {
            String encode = item.encodeFunctionCall();
            Request<?, EthCall> call = web3j.ethCall(Transaction.createEthCallTransaction(DEFAULT_FROM, contract, encode), DefaultBlockParameterName.LATEST);
            batchRequest.add(call);
        });
        List<? extends Response<?>> responses = batchRequest.send().getResponses();
        Iterator<? extends Response<?>> it = responses.iterator();
        Stream.of(calls).forEach(item -> {
            Response<?> res = it.next();
            String hex = res.getResult().toString();
            List<Type> ret = item.decodeFunctionResponse(hex);
            list.add(ret);
        });
        return list;
    }

    /**
     * code for function
     *
     * @param fun function
     * @return code
     */
    public static String encodedFunction(Function fun) {
        String encodedFunction = FunctionEncoder.encode(fun);
        return encodedFunction;
    }

    public static void main(String... args) throws Exception {

    }

    /**
     * byte32
     *
     * @param t
     * @return
     */
    public static Bytes32 formatBytes32String(String t) {
        byte[] temp = t.getBytes();
        int len = temp.length > 32 ? 32 : temp.length;
        byte[] target = new byte[32];
        System.arraycopy(temp, 0, target, 0, len);
        return new Bytes32(target);
    }

    /**
     * 默认web3j
     *
     * @return
     */
    public static Web3j getWeb3j() {
        String nodeUrl = "https://mainnet.infura.io/v3/ac6e4a09bef34cf494d1941d1bc561b6";
        Web3j web3j = Web3j.build(new HttpService(nodeUrl));
        return web3j;
    }

    /**
     * for web3j
     *
     * @param node
     * @return
     */
    public static Web3j getWeb3j(String node) throws ConnectException {
        Web3j web3j;
        if (node.startsWith("ws")) {
            WebSocketService web3jService = new WebSocketService(node, false);
            web3jService.connect();
            web3j = Web3j.build(web3jService);
        } else {
            web3j = Web3j.build(new HttpService(node));
        }
        return web3j;
    }

    /**
     * 指定interval
     *
     * @param node
     * @param pollingInterval
     * @return
     */
    public static Web3j getWeb3j(String node, long pollingInterval) throws ConnectException {
        Web3j web3j;
        if (node.startsWith("ws")) {
            WebSocketService web3jService = new WebSocketService(node, false);
            web3jService.connect();
            web3j = Web3j.build(web3jService, pollingInterval, Async.defaultExecutorService());
        } else {
            web3j = Web3j.build(new HttpService(node), pollingInterval, Async.defaultExecutorService());
        }
        return web3j;
    }

    /**
     * web3j
     *
     * @param okClient
     * @param pollingInterval
     * @return
     */
    public static Web3j getWeb3j(String node, OkHttpClient okClient, long pollingInterval) throws ConnectException {
        Web3j web3j;
        if (node.startsWith("ws")) {
            WebSocketService web3jService = new WebSocketService(node, false);
            web3jService.connect();
            web3j = Web3j.build(web3jService, pollingInterval, Async.defaultExecutorService());
        } else {
            web3j = Web3j.build(new HttpService(node, okClient), pollingInterval, Async.defaultExecutorService());
        }
        return web3j;
    }

    /**
     * one for decimals
     *
     * @param decimals
     * @return
     */
    public static BigInteger one(int decimals) {
        BigInteger bi = BigInteger.valueOf(10);
        return bi.pow(decimals);
    }

    /**
     * n for decimals
     *
     * @param decimals
     * @return
     */
    public static BigInteger n(int n, int decimals) {
        return BigInteger.valueOf(n).multiply(one(decimals));
    }

    /**
     * n for decimals
     *
     * @param n
     * @param decimals
     * @return
     */
    public static BigInteger n(double n, int decimals) {
        return BigDecimal.valueOf(n).multiply(new BigDecimal(one(decimals))).toBigInteger();
    }

    /**
     * decimals
     *
     * @param d
     * @return
     */
    public static BigDecimal decimalsToNumber(int d) {
        return new BigDecimal(one(d));
    }

    /**
     * 当前高度
     *
     * @param web3j
     * @return
     * @throws Exception
     */
    public static long currentHeight(Web3j web3j) throws Exception {
        EthBlockNumber number = web3j.ethBlockNumber().send();
        return number.getBlockNumber().longValue();
    }

    /**
     * 求contract地址
     *
     * @param address
     * @param nonce
     * @return
     */
    public static String calculateContractAddress(String address, long nonce) {
        byte[] addressAsBytes = Numeric.hexStringToByteArray(address);

        byte[] calculatedAddressAsBytes =
                Hash.sha3(RlpEncoder.encode(
                        new RlpList(
                                RlpString.create(addressAsBytes),
                                RlpString.create((nonce)))));

        calculatedAddressAsBytes = Arrays.copyOfRange(calculatedAddressAsBytes,
                12, calculatedAddressAsBytes.length);
        String calculatedAddressAsHex = Numeric.toHexString(calculatedAddressAsBytes);
        return calculatedAddressAsHex;
    }


    /**
     * checksum address
     *
     * @param address
     * @return
     */
    public static String toChecksumAddress(String address) {
        return Keys.toChecksumAddress(address);
    }
}
