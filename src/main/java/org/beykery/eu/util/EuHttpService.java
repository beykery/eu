package org.beykery.eu.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.Buffer;
import okio.BufferedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Service;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.http.HttpService;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static okhttp3.ConnectionSpec.CLEARTEXT;

/**
 * eu http service
 */
public class EuHttpService extends Service {

    /**
     * 随机
     */
    private static final Random random = new SecureRandom();
    /**
     * mapper
     */
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Copied from {@link ConnectionSpec#APPROVED_CIPHER_SUITES}.
     */
    @SuppressWarnings("JavadocReference")
    private static final CipherSuite[] INFURA_CIPHER_SUITES =
            new CipherSuite[]{
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,

                    // Note that the following cipher suites are all on HTTP/2's bad cipher suites list.
                    // We'll
                    // continue to include them until better suites are commonly available. For example,
                    // none
                    // of the better cipher suites listed above shipped with Android 4.4 or Java 7.
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA,

                    // Additional INFURA CipherSuites
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
                    CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256,
                    CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256
            };

    private static final ConnectionSpec INFURA_CIPHER_SUITE_SPEC =
            new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .cipherSuites(INFURA_CIPHER_SUITES)
                    .build();

    /**
     * The list of {@link ConnectionSpec} instances used by the connection.
     */
    private static final List<ConnectionSpec> CONNECTION_SPEC_LIST =
            Arrays.asList(INFURA_CIPHER_SUITE_SPEC, CLEARTEXT);

    public static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    public static final String DEFAULT_URL = "http://localhost:8545/";

    private static final Logger log = LoggerFactory.getLogger(HttpService.class);

    private OkHttpClient httpClient;

    private List<String> urls;

    private final boolean includeRawResponse;

    private HashMap<String, String> headers = new HashMap<>();

    /**
     * multi urls
     *
     * @param urls
     */
    public EuHttpService(List<String> urls) {
        this(urls, createOkHttpClient(), false);
    }

    public EuHttpService(List<String> urls, OkHttpClient httpClient, boolean includeRawResponses) {
        super(includeRawResponses);
        this.urls = urls;
        this.httpClient = httpClient;
        this.includeRawResponse = includeRawResponses;
    }

    /**
     * with client config
     *
     * @param urls
     * @param httpClient
     */
    public EuHttpService(List<String> urls, OkHttpClient httpClient) {
        this(urls, httpClient, false);
    }

    public EuHttpService(OkHttpClient httpClient, boolean includeRawResponses) {
        this(Arrays.asList(DEFAULT_URL), httpClient, includeRawResponses);
    }

    public EuHttpService(String url, OkHttpClient httpClient) {
        this(Arrays.asList(url), httpClient, false);
    }

    public EuHttpService(String url) {
        this(url, createOkHttpClient());
    }

    public EuHttpService(String url, boolean includeRawResponse) {
        this(Arrays.asList(url), createOkHttpClient(), includeRawResponse);
    }

    public EuHttpService(OkHttpClient httpClient) {
        this(DEFAULT_URL, httpClient);
    }

    public EuHttpService(boolean includeRawResponse) {
        this(DEFAULT_URL, includeRawResponse);
    }

    public EuHttpService() {
        this(DEFAULT_URL);
    }

    public static OkHttpClient.Builder getOkHttpClientBuilder() {
        final OkHttpClient.Builder builder =
                new OkHttpClient.Builder().connectionSpecs(CONNECTION_SPEC_LIST);
        configureLogging(builder);
        return builder;
    }

    /**
     * with default client config
     *
     * @return
     */
    public static OkHttpClient createOkHttpClient() {
        OkHttpClient client = getOkHttpClientBuilder().build();
        return client;
    }

    /**
     * timeout for ok http client
     *
     * @param connectTimeout
     * @param readTimeout
     * @param writeTimeout
     * @return
     */
    public static OkHttpClient createOkHttpClient(long connectTimeout, long readTimeout, long writeTimeout) {
        return getOkHttpClientBuilder()
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
                .build();
    }

    private static void configureLogging(OkHttpClient.Builder builder) {
        if (log.isDebugEnabled()) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor(log::debug);
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }
    }

    @Override
    protected InputStream performIO(String request) throws IOException {

        RequestBody requestBody = RequestBody.create(request, JSON_MEDIA_TYPE);
        Headers headers = buildHeaders();

        String url = getUrl();
        okhttp3.Request httpRequest =
                new okhttp3.Request.Builder().url(url).headers(headers).post(requestBody).build();

        try (okhttp3.Response response = httpClient.newCall(httpRequest).execute()) {
            processHeaders(response.headers());
            ResponseBody responseBody = response.body();
            if (response.isSuccessful()) {
                if (responseBody != null) {
                    return buildInputStream(url, responseBody);
                } else {
                    return null;
                }
            } else {
                int code = response.code();
                String text = responseBody == null ? "N/A" : responseBody.string();

                throw new ClientConnectionException(url + " Invalid response received: " + code + "; " + text);
            }
        }
    }

    protected void processHeaders(Headers headers) {
        // Default implementation is empty
    }

    private InputStream buildInputStream(String url, ResponseBody responseBody) throws IOException {
        if (includeRawResponse) {
            // we have to buffer the entire input payload, so that after processing
            // it can be re-read and used to populate the rawResponse field.

            BufferedSource source = responseBody.source();
            source.request(Long.MAX_VALUE); // Buffer the entire body
            Buffer buffer = source.getBuffer();

            long size = buffer.size();
            if (size > Integer.MAX_VALUE) {
                throw new UnsupportedOperationException(
                        "Non-integer input buffer size specified: " + size);
            }

            int bufferSize = (int) size;
            InputStream inputStream = responseBody.byteStream();

            BufferedInputStream bufferedinputStream =
                    new BufferedInputStream(inputStream, bufferSize);

            bufferedinputStream.mark(inputStream.available());
            return bufferedinputStream;

        } else {
            byte[] bytes = responseBody.bytes();
            // 检查下result
            JsonNode node = mapper.readTree(bytes);
            if (!node.isArray()) {
                JsonNode err = node.get("error");
                if (err != null) {
                    throw new ClientConnectionException(MessageFormat.format("{0} Invalid response received: {1}", url, err));
                }
            }
            return new ByteArrayInputStream(bytes);
        }
    }

    private Headers buildHeaders() {
        return Headers.of(headers);
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public void addHeaders(Map<String, String> headersToAdd) {
        headers.putAll(headersToAdd);
    }

    public HashMap<String, String> getHeaders() {
        return headers;
    }

    /**
     * 随机选择一个url
     *
     * @return
     */
    public String getUrl() {
        int r = random.nextInt(urls.size());
        String old = urls.get(r);
        return old;
//        String u;
//        if (old.indexOf('?') > 0) {
//            u = old + "&eu=" + r;
//        } else {
//            u = old + "?eu=" + r;
//        }
//        return u;
    }

    @Override
    public void close() throws IOException {
    }
}

