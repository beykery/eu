package org.beykery.eu.util;

import okhttp3.OkHttpClient;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
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
import java.math.RoundingMode;
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
    public static List<Type> call(
            Web3j web3j,
            String contractAddress,
            String funname,
            List<Type> inputs,
            List<TypeReference<?>> outputs
    ) throws Exception {
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
                return numberWithDecimals(balance, d, 0);
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
        return numberWithDecimals(v.getValue(), decimals, 0);
    }

    /**
     * number with decimals
     *
     * @param v
     * @param decimals
     * @return
     */
    public static double numberWithDecimals(BigInteger v, int decimals) {
        return numberWithDecimals(v, decimals, 0);
    }

    /**
     * @param v         value
     * @param decimals  小数位数
     * @param precision 放大次数
     * @return
     */
    public static double numberWithDecimals(BigInteger v, int decimals, int precision) {
        BigDecimal bd = BigDecimal.valueOf(10);
        bd = bd.pow(decimals);
        BigDecimal ret;
        if (precision > 0) {
            ret = new BigDecimal(v.multiply(BigInteger.valueOf(10).pow(precision))).divide(bd, decimals, RoundingMode.DOWN);
        } else {
            ret = new BigDecimal(v).divide(bd, decimals, RoundingMode.DOWN);
        }
        return ret.doubleValue();
    }

    /**
     * chain id
     *
     * @param web3j
     * @return
     */
    public static long chainId(Web3j web3j) throws IOException {
        EthChainId ret = web3j.ethChainId().send();
        return ret.getId();
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

    /**
     * byte32
     *
     * @param t
     * @return
     */
    public static Bytes32 formatBytes32String(String t) {
        byte[] temp = t.getBytes();
        int len = Math.min(temp.length, 32);
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
        return getWeb3j(node, 3);
    }

    /**
     * 指定interval
     *
     * @param node
     * @param pollingInterval
     * @return
     */
    public static Web3j getWeb3j(String node, long pollingInterval) throws ConnectException {
        Web3jService ws;
        if (node.startsWith("ws")) {
            ws = new WebSocketService(node, false);
            ((WebSocketService) ws).connect();
        } else {
            ws = new EuHttpService(node);
        }
        Web3j web3j = Web3j.build(ws, pollingInterval, Async.defaultExecutorService());
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
        Web3jService ws;
        if (node.startsWith("ws")) {
            ws = new WebSocketService(node, false);
            ((WebSocketService) ws).connect();
        } else {
            ws = new EuHttpService(node, okClient);
        }
        Web3j web3j = Web3j.build(ws, pollingInterval, Async.defaultExecutorService());
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

    /**
     * 签名交易
     *
     * @param privateKey 私钥
     * @param price      gas price
     * @param limit      gas limit
     * @param to         合约地址或者外部地址
     * @param value      转账的ht数额
     * @param data       合约调用函数签名
     * @return signed transaction
     */
    public static String signTransaction(
            String privateKey,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            String data,
            BigInteger nonce,
            long chainId
    ) {
        RawTransaction rawTransaction = rawTransaction(nonce, price, limit, to, value, data);
        return signTransaction(privateKey, rawTransaction, chainId);
    }

    /**
     * 签名交易
     *
     * @param privateKey
     * @param price
     * @param limit
     * @param to
     * @param value
     * @param data
     * @param nonce
     * @return
     */
    public static String signTransaction(
            String privateKey,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            String data,
            BigInteger nonce
    ) {
        return signTransaction(privateKey, price, limit, to, value, data, nonce, 0);
    }

    /**
     * address for private key
     *
     * @param privateKey 私钥
     * @return 地址
     */
    public static String address(String privateKey) {
        Credentials credentials = Credentials.create(privateKey);
        BigInteger number = credentials.getEcKeyPair().getPublicKey();
        return Keys.toChecksumAddress(Keys.getAddress(number));
    }

    /**
     * 随机私钥
     *
     * @return
     */
    public static String randomPrivate() {
        try {
            ECKeyPair ecKeyPair = Keys.createEcKeyPair();
            BigInteger privateKeyInDec = ecKeyPair.getPrivateKey();
            String pri = "0x" + privateKeyInDec.toString(16);
            return pri;
        } catch (Exception ex) {
        }
        return null;
    }

    /**
     * 组织一个raw transaction
     *
     * @param nonce nonce
     * @param price gas价格
     * @param limit gas limit
     * @param to    合约或外部地址
     * @param value 转账的ht数额
     * @param data  消息（合约调用的方法签名）
     * @return raw transaction
     */
    public static RawTransaction rawTransaction(
            BigInteger nonce,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            String data
    ) {
        RawTransaction tr = RawTransaction.createTransaction(nonce, price, limit, to, value, data);
        return tr;
    }

    /**
     * 组织一个raw transaction
     * eip1559
     *
     * @param nonce
     * @param limit
     * @param to
     * @param value
     * @param data
     * @return
     */
    public static RawTransaction rawTransaction(
            long chainId,
            BigInteger nonce,
            BigInteger limit,
            String to,
            BigInteger value,
            String data,
            BigInteger maxPriorityFeePerGas,
            BigInteger maxFeePerGas
    ) {
        RawTransaction tr = RawTransaction.createTransaction(chainId, nonce, limit, to, value, data, maxPriorityFeePerGas, maxFeePerGas);
        return tr;
    }

    /**
     * 本地签名
     *
     * @param privateKey          私钥
     * @param unsignedTransaction 待签名交易
     * @return signed transaction
     */
    public static String signTransaction(String privateKey, RawTransaction unsignedTransaction, long chainId) {
        Credentials credentials = Credentials.create(privateKey);
        byte[] signedMessage = chainId > 0 ? TransactionEncoder.signMessage(unsignedTransaction, chainId, credentials) : TransactionEncoder.signMessage(unsignedTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);
        return hexValue;
    }

    /**
     * 本地签名
     *
     * @param privateKey
     * @param unsignedTransaction
     * @return
     */
    public static String signTransaction(String privateKey, RawTransaction unsignedTransaction) {
        return signTransaction(privateKey, unsignedTransaction, 0);
    }

    /**
     * nonce for address
     *
     * @param address 外部地址
     * @return nonce
     */
    public static BigInteger nonce(Web3j web3j, String address) {
        return nonce(web3j, address, DefaultBlockParameterName.LATEST);
    }

    /**
     * nonce of address
     *
     * @param web3j
     * @param address
     * @param param
     * @return
     */
    public static BigInteger nonce(Web3j web3j, String address, DefaultBlockParameterName param) {
        try {
            EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(address, param).sendAsync().get();
            BigInteger nonce = ethGetTransactionCount.getTransactionCount();
            return nonce;
        } catch (InterruptedException | ExecutionException ex) {
            return BigInteger.valueOf(-1);
        }
    }

    /**
     * 签名并发送交易
     *
     * @param privateKey 私钥
     * @param price      gas price
     * @param limit      gas limit
     * @param to         合约或外部地址
     * @param value      转账的ht数额
     * @param data       调用合约方法签名
     * @return
     * @throws Exception
     */
    public static EthSendTransaction signAndSendTransaction(
            Web3j web3j,
            String privateKey,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            String data,
            BigInteger nonce,
            long chainId
    ) throws Exception {
        String signedTransaction = signTransaction(privateKey, price, limit, to, value, data, nonce, chainId);
        return sendSignedTransaction(web3j, signedTransaction);
    }

    /**
     * 签名并发送交易
     *
     * @param web3j
     * @param privateKey
     * @param price
     * @param limit
     * @param to
     * @param value
     * @param data
     * @param nonce
     * @return
     * @throws Exception
     */
    public static EthSendTransaction signAndSendTransaction(
            Web3j web3j,
            String privateKey,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            String data,
            BigInteger nonce
    ) throws Exception {
        return signAndSendTransaction(web3j, privateKey, price, limit, to, value, data, nonce, 0);
    }

    /**
     * send signed transaction
     *
     * @param signedTransaction signed transaction
     * @return 结果（hash）
     */
    public static EthSendTransaction sendSignedTransaction(Web3j web3j, String signedTransaction) throws Exception {
        final EthSendTransaction result = web3j.ethSendRawTransaction(signedTransaction).send();
        return result;
    }

    /**
     * decode input data
     *
     * @param inputData
     * @param outputParameters
     * @return
     */
    public static List<Type> decodeInputData(String inputData, List<TypeReference<?>> outputParameters) {
        List<Type> result = FunctionReturnDecoder.decode(
                inputData.substring(10),
                convert(outputParameters)
        );
        return result;
    }

    /**
     * ? to Type
     *
     * @param input
     * @return
     */
    public static List<TypeReference<Type>> convert(List<TypeReference<?>> input) {
        List<TypeReference<Type>> result = new ArrayList<>(input.size());
        result.addAll(
                input.stream()
                        .map(typeReference -> (TypeReference<Type>) typeReference)
                        .collect(Collectors.toList()));
        return result;
    }

    /**
     * encode function
     *
     * @param f
     * @return
     */
    public static String encode(Function f) {
        final String encode = FunctionEncoder.encode(f);
        return encode;
    }

    /**
     * @param f
     * @return
     */
    public static String buildMethodId(Function f) {
        String signature = buildMethodSignature(f.getName(), f.getInputParameters());
        String id = buildMethodId(signature);
        return id;
    }

    /**
     * method id
     *
     * @param methodName
     * @param parameters
     * @return
     */
    public static String buildMethodId(final String methodName, final List<Type> parameters) {
        String signature = buildMethodSignature(methodName, parameters);
        String id = buildMethodId(signature);
        return id;
    }

    /**
     * method signature
     *
     * @param methodName
     * @param parameters
     * @return
     */
    public static String buildMethodSignature(final String methodName, final List<Type> parameters) {
        final StringBuilder result = new StringBuilder();
        result.append(methodName);
        result.append("(");
        final String params =
                parameters.stream().map(Type::getTypeAsString).collect(Collectors.joining(","));
        result.append(params);
        result.append(")");
        return result.toString();
    }

    /**
     * method id
     *
     * @param methodSignature
     * @return
     */
    public static String buildMethodId(final String methodSignature) {
        final byte[] input = methodSignature.getBytes();
        final byte[] hash = Hash.sha3(input);
        return Numeric.toHexString(hash).substring(0, 10);
    }

    /**
     * sign function for contract
     *
     * @param privateKey
     * @param price
     * @param limit
     * @param to
     * @param value
     * @param f
     * @param nonce
     * @return
     */
    public static String signTransaction(
            String privateKey,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            Function f,
            BigInteger nonce,
            long chainId
    ) {
        String data = encode(f);
        return signTransaction(privateKey, price, limit, to, value, data, nonce, chainId);
    }

    /**
     * sign function for contract
     *
     * @param privateKey
     * @param price
     * @param limit
     * @param to
     * @param value
     * @param f
     * @param nonce
     * @return
     */
    public static String signTransaction(
            String privateKey,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            Function f,
            BigInteger nonce
    ) {
        return signTransaction(privateKey, price, limit, to, value, f, nonce, 0);
    }

    /**
     * pending tx filter id
     *
     * @param web3j
     * @return
     * @throws IOException
     */
    public static BigInteger newPendingTransactionFilterId(Web3j web3j) throws IOException {
        EthFilter filter = web3j.ethNewPendingTransactionFilter().send();
        BigInteger fid = filter.getFilterId();
        return fid;
    }

    /**
     * pending transactions
     *
     * @param web3j
     * @param filterId
     * @param parallel
     * @return
     */
    public static List<org.web3j.protocol.core.methods.response.Transaction> pendingTransactions(Web3j web3j, BigInteger filterId, boolean parallel) throws IOException {
        EthLog log = web3j.ethGetFilterChanges(filterId).send();
        List<EthLog.LogResult> ls = log.getLogs();
        if (ls != null && ls.size() > 0) {
            Stream<EthLog.LogResult> stream = parallel ? ls.parallelStream() : ls.stream();
            List<org.web3j.protocol.core.methods.response.Transaction> ret = stream.map(item -> {
                org.web3j.protocol.core.methods.response.Transaction tx = null;
                try {
                    String hash = item.get().toString();
                    EthTransaction et = web3j.ethGetTransactionByHash(hash).send();
                    if (et.getTransaction().isPresent()) {
                        tx = et.getTransaction().get();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                return tx;
            }).filter(Objects::nonNull).collect(Collectors.toList());
            ret.sort((t1, t2) -> t2.getGasPrice().compareTo(t1.getGasPrice()));
            return ret;
        }
        return Collections.EMPTY_LIST;
    }
}
