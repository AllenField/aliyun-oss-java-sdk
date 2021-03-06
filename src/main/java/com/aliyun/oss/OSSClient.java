/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.aliyun.oss;

import static com.aliyun.oss.common.utils.CodingUtils.assertParameterNotNull;
import static com.aliyun.oss.common.utils.IOUtils.checkFile;
import static com.aliyun.oss.internal.OSSConstants.DEFAULT_CHARSET_NAME;
import static com.aliyun.oss.internal.OSSConstants.DEFAULT_OSS_ENDPOINT;
import static com.aliyun.oss.internal.OSSUtils.OSS_RESOURCE_MANAGER;
import static com.aliyun.oss.internal.OSSUtils.ensureBucketNameValid;
import static com.aliyun.oss.internal.OSSUtils.populateResponseHeaderParameters;
import static com.aliyun.oss.internal.RequestParameters.OSS_ACCESS_KEY_ID;
import static com.aliyun.oss.internal.RequestParameters.SECURITY_TOKEN;
import static com.aliyun.oss.internal.RequestParameters.SIGNATURE;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aliyun.oss.common.auth.Credentials;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.auth.ServiceSignature;
import com.aliyun.oss.common.comm.DefaultServiceClient;
import com.aliyun.oss.common.comm.RequestMessage;
import com.aliyun.oss.common.comm.ResponseMessage;
import com.aliyun.oss.common.comm.ServiceClient;
import com.aliyun.oss.common.comm.TimeoutServiceClient;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.common.utils.DateUtil;
import com.aliyun.oss.common.utils.HttpHeaders;
import com.aliyun.oss.common.utils.HttpUtil;
import com.aliyun.oss.internal.CORSOperation;
import com.aliyun.oss.internal.LiveChannelOperation;
import com.aliyun.oss.internal.OSSBucketOperation;
import com.aliyun.oss.internal.OSSDownloadOperation;
import com.aliyun.oss.internal.OSSHeaders;
import com.aliyun.oss.internal.OSSMultipartOperation;
import com.aliyun.oss.internal.OSSObjectOperation;
import com.aliyun.oss.internal.OSSUploadOperation;
import com.aliyun.oss.internal.OSSUtils;
import com.aliyun.oss.internal.SignUtils;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.AccessControlList;
import com.aliyun.oss.model.AppendObjectRequest;
import com.aliyun.oss.model.AppendObjectResult;
import com.aliyun.oss.model.Bucket;
import com.aliyun.oss.model.BucketInfo;
import com.aliyun.oss.model.BucketList;
import com.aliyun.oss.model.BucketLoggingResult;
import com.aliyun.oss.model.BucketReferer;
import com.aliyun.oss.model.BucketReplicationProgress;
import com.aliyun.oss.model.BucketWebsiteResult;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.CnameConfiguration;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadResult;
import com.aliyun.oss.model.CopyObjectRequest;
import com.aliyun.oss.model.CopyObjectResult;
import com.aliyun.oss.model.CreateBucketRequest;
import com.aliyun.oss.model.CreateLiveChannelRequest;
import com.aliyun.oss.model.CreateLiveChannelResult;
import com.aliyun.oss.model.DeleteBucketCnameRequest;
import com.aliyun.oss.model.DeleteBucketReplicationRequest;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.DeleteObjectsResult;
import com.aliyun.oss.model.DownloadFileRequest;
import com.aliyun.oss.model.DownloadFileResult;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.GenerateRtmpUriRequest;
import com.aliyun.oss.model.GenerateVodPlaylistRequest;
import com.aliyun.oss.model.GenericRequest;
import com.aliyun.oss.model.GetBucketImageResult;
import com.aliyun.oss.model.GetBucketReplicationProgressRequest;
import com.aliyun.oss.model.ListLiveChannelsRequest;
import com.aliyun.oss.model.LiveChannel;
import com.aliyun.oss.model.LiveChannelGenericRequest;
import com.aliyun.oss.model.LiveChannelInfo;
import com.aliyun.oss.model.LiveChannelListing;
import com.aliyun.oss.model.LiveChannelStat;
import com.aliyun.oss.model.LiveChannelStatus;
import com.aliyun.oss.model.LiveRecord;
import com.aliyun.oss.model.ReplicationRule;
import com.aliyun.oss.model.GetImageStyleResult;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.HeadObjectRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.LifecycleRule;
import com.aliyun.oss.model.ListBucketsRequest;
import com.aliyun.oss.model.ListMultipartUploadsRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ListPartsRequest;
import com.aliyun.oss.model.MultipartUploadListing;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectAcl;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OptionsRequest;
import com.aliyun.oss.model.PartListing;
import com.aliyun.oss.model.PolicyConditions;
import com.aliyun.oss.model.PutBucketImageRequest;
import com.aliyun.oss.model.PutImageStyleRequest;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyun.oss.model.SetBucketAclRequest;
import com.aliyun.oss.model.SetBucketCORSRequest;
import com.aliyun.oss.model.SetLiveChannelRequest;
import com.aliyun.oss.model.UploadFileRequest;
import com.aliyun.oss.model.UploadFileResult;
import com.aliyun.oss.model.SetBucketCORSRequest.CORSRule;
import com.aliyun.oss.model.AddBucketCnameRequest;
import com.aliyun.oss.model.SetBucketLifecycleRequest;
import com.aliyun.oss.model.SetBucketLoggingRequest;
import com.aliyun.oss.model.SetBucketRefererRequest;
import com.aliyun.oss.model.AddBucketReplicationRequest;
import com.aliyun.oss.model.SetBucketStorageCapacityRequest;
import com.aliyun.oss.model.SetBucketTaggingRequest;
import com.aliyun.oss.model.SetBucketWebsiteRequest;
import com.aliyun.oss.model.SetObjectAclRequest;
import com.aliyun.oss.model.SimplifiedObjectMeta;
import com.aliyun.oss.model.TagSet;
import com.aliyun.oss.model.Style;
import com.aliyun.oss.model.UploadPartCopyRequest;
import com.aliyun.oss.model.UploadPartCopyResult;
import com.aliyun.oss.model.UploadPartRequest;
import com.aliyun.oss.model.UploadPartResult;
import com.aliyun.oss.model.UserQos;

/**
 * 访问阿里云对象存储服务（Object Storage Service， OSS）的入口类。
 */
public class OSSClient implements OSS {

    /* The default credentials provider */
    private CredentialsProvider credsProvider;

    /* The valid endpoint for accessing to OSS services */
    private URI endpoint;

    /* The default service client */
    private ServiceClient serviceClient;

    /* The miscellaneous OSS operations */
    private OSSBucketOperation bucketOperation;
    private OSSObjectOperation objectOperation;
    private OSSMultipartOperation multipartOperation;
    private CORSOperation corsOperation;
    private OSSUploadOperation uploadOperation;
    private OSSDownloadOperation downloadOperation;
    private LiveChannelOperation liveChannelOperation;

    /**
     * 使用默认的OSS Endpoint(http://oss-cn-hangzhou.aliyuncs.com)及
     * 阿里云颁发的Access Id/Access Key构造一个新的{@link OSSClient}对象。
     * 
     * @param accessKeyId
     *            访问OSS的Access Key ID。
     * @param secretAccessKey
     *            访问OSS的Secret Access Key。
     */
    @Deprecated
    public OSSClient(String accessKeyId, String secretAccessKey) {
        this(DEFAULT_OSS_ENDPOINT, new DefaultCredentialProvider(accessKeyId, secretAccessKey));
    }

    /**
     * 使用指定的OSS Endpoint、阿里云颁发的Access Id/Access Key构造一个新的{@link OSSClient}对象。
     * 
     * @param endpoint
     *            OSS服务的Endpoint。
     * @param accessKeyId
     *            访问OSS的Access Key ID。
     * @param secretAccessKey
     *            访问OSS的Secret Access Key。
     */
    public OSSClient(String endpoint, String accessKeyId, String secretAccessKey) {
        this(endpoint, new DefaultCredentialProvider(accessKeyId, secretAccessKey), null);
    }
    
    /**
     * 使用指定的OSS Endpoint、STS提供的临时Token信息(Access Id/Access Key/Security Token)
     * 构造一个新的{@link OSSClient}对象。
     * 
     * @param endpoint
     *            OSS服务的Endpoint。
     * @param accessKeyId
     *            STS提供的临时访问ID。
     * @param secretAccessKey
     *            STS提供的访问密钥。
     * @param securityToken
     *               STS提供的安全令牌。
     */
    public OSSClient(String endpoint, String accessKeyId, String secretAccessKey, String securityToken) {
        this(endpoint, new DefaultCredentialProvider(accessKeyId, secretAccessKey, securityToken), null);
    }
    
    /**
     * 使用指定的OSS Endpoint、阿里云颁发的Access Id/Access Key、客户端配置
     * 构造一个新的{@link OSSClient}对象。
     * 
     * @param endpoint
     *            OSS服务的Endpoint。
     * @param accessKeyId
     *            访问OSS的Access Key ID。
     * @param secretAccessKey
     *            访问OSS的Secret Access Key。
     * @param config
     *            客户端配置 {@link ClientConfiguration}。 如果为null则会使用默认配置。
     */
    public OSSClient(String endpoint, String accessKeyId, String secretAccessKey, 
            ClientConfiguration config) {
        this(endpoint, new DefaultCredentialProvider(accessKeyId, secretAccessKey), config);
    }
    
    /**
     * 使用指定的OSS Endpoint、STS提供的临时Token信息(Access Id/Access Key/Security Token)、
     * 客户端配置构造一个新的{@link OSSClient}对象。
     * 
     * @param endpoint
     *            OSS服务的Endpoint。
     * @param accessKeyId
     *            STS提供的临时访问ID。
     * @param secretAccessKey
     *            STS提供的访问密钥。
     * @param securityToken
     *               STS提供的安全令牌。
     * @param config
     *            客户端配置 {@link ClientConfiguration}。 如果为null则会使用默认配置。
     */
    public OSSClient(String endpoint, String accessKeyId, String secretAccessKey, String securityToken, 
            ClientConfiguration config) {
        this(endpoint, new DefaultCredentialProvider(accessKeyId, secretAccessKey, securityToken), config);
    }

    /**
     * 使用默认配置及指定的{@link CredentialsProvider}与Endpoint构造一个新的{@link OSSClient}对象。
     * @param endpoint OSS services的Endpoint。
     * @param credsProvider Credentials提供者。
     */
    public OSSClient(String endpoint, CredentialsProvider credsProvider) {
        this(endpoint, credsProvider, null);
    }
    
    /**
     * 使用指定的{@link CredentialsProvider}、配置及Endpoint构造一个新的{@link OSSClient}对象。
     * @param endpoint OSS services的Endpoint。
     * @param credsProvider Credentials提供者。
     * @param config client配置。
     */
    public OSSClient(String endpoint, CredentialsProvider credsProvider, ClientConfiguration config) {
        this.credsProvider = credsProvider;
        config = config == null ? new ClientConfiguration() : config;
        if (config.isRequestTimeoutEnabled()) {
            this.serviceClient = new TimeoutServiceClient(config);
        } else {
            this.serviceClient = new DefaultServiceClient(config);
        }
        initOperations();
        setEndpoint(endpoint);
    }
    
    /**
     * 获取OSS services的Endpoint。
     * @return OSS services的Endpoint。
     */
    public synchronized URI getEndpoint() {
        return URI.create(endpoint.toString());
    }
    
    /**
     * 设置OSS services的Endpoint。
     * @param endpoint OSS services的Endpoint。
     */
    public synchronized void setEndpoint(String endpoint) {
        URI uri = toURI(endpoint);
        this.endpoint = uri;
        
        if (isIpOrLocalhost(uri)) {
            serviceClient.getClientConfiguration().setSLDEnabled(true);
        }
        
        this.bucketOperation.setEndpoint(uri);
        this.objectOperation.setEndpoint(uri);
        this.multipartOperation.setEndpoint(uri);
        this.corsOperation.setEndpoint(uri);
        this.liveChannelOperation.setEndpoint(uri);
    }
    
    /**
     * 判定一个网络地址是否是IP还是域名。IP都是用二级域名，域名(Localhost除外)不使用二级域名。
     * @param uri URI。
     */
    private boolean isIpOrLocalhost(URI uri){
        if (uri.getHost().equals("localhost")) {
            return true;
        }
        
        InetAddress ia;
        try {
            ia = InetAddress.getByName(uri.getHost());
        } catch (UnknownHostException e) {
            return false;
        }
        
        if (ia.getHostName().equals(ia.getHostAddress())) {
            return true;
        }
        
        return false;
    }
    
    private URI toURI(String endpoint) throws IllegalArgumentException {        
        if (!endpoint.contains("://")) {
            ClientConfiguration conf = this.serviceClient.getClientConfiguration();
            endpoint = conf.getProtocol().toString() + "://" + endpoint;
        }

        try {
            return new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    private void initOperations() {
        this.bucketOperation = new OSSBucketOperation(this.serviceClient, this.credsProvider);
        this.objectOperation = new OSSObjectOperation(this.serviceClient, this.credsProvider);
        this.multipartOperation = new OSSMultipartOperation(this.serviceClient, this.credsProvider);
        this.corsOperation = new CORSOperation(this.serviceClient, this.credsProvider);
        this.uploadOperation = new OSSUploadOperation(this.multipartOperation);
        this.downloadOperation = new OSSDownloadOperation(objectOperation);
        this.liveChannelOperation = new LiveChannelOperation(this.serviceClient, this.credsProvider);
    }
    
    @Override
    public void switchCredentials(Credentials creds) {
        if (creds == null) {
            throw new IllegalArgumentException("creds should not be null.");
        }
        
        this.credsProvider.setCredentials(creds);
    }
    
    public CredentialsProvider getCredentialsProvider() {
        return this.credsProvider;
    }
    
    public ClientConfiguration getClientConfiguration() {
        return serviceClient.getClientConfiguration();
    }

    @Override
    public Bucket createBucket(String bucketName) 
            throws OSSException, ClientException {
        return this.createBucket(new CreateBucketRequest(bucketName));
    }

    @Override
    public Bucket createBucket(CreateBucketRequest createBucketRequest)
            throws OSSException, ClientException {
        return bucketOperation.createBucket(createBucketRequest);
    }

    @Override
    public void deleteBucket(String bucketName) 
            throws OSSException, ClientException {
        this.deleteBucket(new GenericRequest(bucketName));
    }
    
    @Override
    public void deleteBucket(GenericRequest genericRequest)
            throws OSSException, ClientException {
        bucketOperation.deleteBucket(genericRequest);
    }

    @Override
    public List<Bucket> listBuckets() throws OSSException, ClientException {
        return bucketOperation.listBuckets();
    }

    @Override
    public BucketList listBuckets(ListBucketsRequest listBucketsRequest) 
            throws OSSException, ClientException {
        return bucketOperation.listBuckets(listBucketsRequest);
    }

    @Override
    public BucketList listBuckets(String prefix, String marker, Integer maxKeys) 
            throws OSSException, ClientException {
        return bucketOperation.listBuckets(new ListBucketsRequest(prefix, marker, maxKeys));
    }

    @Override
    public void setBucketAcl(String bucketName, CannedAccessControlList cannedACL) 
            throws OSSException, ClientException {
        this.setBucketAcl(new SetBucketAclRequest(bucketName, cannedACL));
    }
    
    @Override
    public void setBucketAcl(SetBucketAclRequest setBucketAclRequest)
            throws OSSException, ClientException {
        bucketOperation.setBucketAcl(setBucketAclRequest);
    }

    @Override
    public AccessControlList getBucketAcl(String bucketName) 
            throws OSSException, ClientException {
        return this.getBucketAcl(new GenericRequest(bucketName));
    }
    
    @Override
    public AccessControlList getBucketAcl(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return bucketOperation.getBucketAcl(genericRequest);
    }
     
     @Override
     public void setBucketReferer(String bucketName, BucketReferer referer) 
             throws OSSException, ClientException {
         this.setBucketReferer(new SetBucketRefererRequest(bucketName, referer));
     }
     
     @Override
     public void setBucketReferer(SetBucketRefererRequest setBucketRefererRequest)
             throws OSSException, ClientException {
         bucketOperation.setBucketReferer(setBucketRefererRequest);
     }
     
     @Override
     public BucketReferer getBucketReferer(String bucketName)
             throws OSSException, ClientException {
         return this.getBucketReferer(new GenericRequest(bucketName));
     }

     @Override
     public BucketReferer getBucketReferer(GenericRequest genericRequest)
             throws OSSException, ClientException {
         return bucketOperation.getBucketReferer(genericRequest);
     }
          
    @Override
    public String getBucketLocation(String bucketName) 
            throws OSSException, ClientException {
        return this.getBucketLocation(new GenericRequest(bucketName));
    }
    
    @Override
    public String getBucketLocation(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return bucketOperation.getBucketLocation(genericRequest);
    }

    @Override
    public boolean doesBucketExist(String bucketName) 
            throws OSSException, ClientException {
        return this.doesBucketExist(new GenericRequest(bucketName));
    }
    
    @Override
    public boolean doesBucketExist(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return bucketOperation.doesBucketExists(genericRequest);
    }

    /**
     * 已过时。请使用{@link OSSClient#doesBucketExist(String)}。
     */
    @Deprecated
    public boolean isBucketExist(String bucketName) 
            throws OSSException, ClientException {
        return this.doesBucketExist(bucketName);
    }

    @Override
    public ObjectListing listObjects(String bucketName) 
            throws OSSException, ClientException {
        return listObjects(new ListObjectsRequest(bucketName, null, null, null, null));
    }

    @Override
    public ObjectListing listObjects(String bucketName, String prefix) 
            throws OSSException, ClientException {
        return listObjects(new ListObjectsRequest(bucketName, prefix, null, null, null));
    }

    @Override
    public ObjectListing listObjects(ListObjectsRequest listObjectsRequest) 
            throws OSSException, ClientException {
        return bucketOperation.listObjects(listObjectsRequest);
    }
    
    @Override
    public PutObjectResult putObject(String bucketName, String key, InputStream input) 
            throws OSSException, ClientException {
        return putObject(bucketName, key, input, null);
    }

    @Override
    public PutObjectResult putObject(String bucketName, String key, InputStream input, ObjectMetadata metadata)
            throws OSSException, ClientException {
        return putObject(new PutObjectRequest(bucketName, key, input, metadata));
    }
    
    @Override
    public PutObjectResult putObject(String bucketName, String key, File file, ObjectMetadata metadata) 
            throws OSSException, ClientException {
        return putObject(new PutObjectRequest(bucketName, key, file, metadata));
    }
    
    @Override
    public PutObjectResult putObject(String bucketName, String key, File file)
            throws OSSException, ClientException {
        return putObject(bucketName, key, file, null);
    }

    @Override
    public PutObjectResult putObject(PutObjectRequest putObjectRequest)
            throws OSSException, ClientException {
        return objectOperation.putObject(putObjectRequest);
    }
    
    @Override
    public PutObjectResult putObject(URL signedUrl, String filePath, Map<String, String> requestHeaders)
            throws OSSException, ClientException {
        return putObject(signedUrl, filePath, requestHeaders, false);
    }
    
    @Override
    public PutObjectResult putObject(URL signedUrl, String filePath, Map<String, String> requestHeaders,
            boolean useChunkEncoding) throws OSSException, ClientException {
        
        FileInputStream requestContent = null;
        try {
            File toUpload = new File(filePath);
            if (!checkFile(toUpload)) {
                throw new IllegalArgumentException("Illegal file path: " + filePath);
            }
            long fileSize = toUpload.length();
            requestContent = new FileInputStream(toUpload);
            
            return putObject(signedUrl, requestContent, fileSize, requestHeaders, useChunkEncoding);
        } catch (FileNotFoundException e) {
            throw new ClientException(e);
        } finally {
            if (requestContent != null) {
                try {
                    requestContent.close();
                } catch (IOException e) {
                }
            }
        }
    }

    @Override
    public PutObjectResult putObject(URL signedUrl, InputStream requestContent, long contentLength, 
            Map<String, String> requestHeaders) 
                    throws OSSException, ClientException {
        return putObject(signedUrl, requestContent, contentLength, requestHeaders, false);
    }
    
    @Override
    public PutObjectResult putObject(URL signedUrl, InputStream requestContent, long contentLength,
            Map<String, String> requestHeaders, boolean useChunkEncoding) 
                    throws OSSException, ClientException {
        return objectOperation.putObject(signedUrl, requestContent, contentLength, requestHeaders, useChunkEncoding);
    }

    @Override
    public CopyObjectResult copyObject(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey) 
            throws OSSException, ClientException {
        return copyObject(new CopyObjectRequest(sourceBucketName, sourceKey, destinationBucketName, destinationKey));
    }

    @Override
    public CopyObjectResult copyObject(CopyObjectRequest copyObjectRequest) 
            throws OSSException, ClientException {
        return objectOperation.copyObject(copyObjectRequest);
    }

    @Override
    public OSSObject getObject(String bucketName, String key) 
            throws OSSException, ClientException {
        return this.getObject(new GetObjectRequest(bucketName, key));
    }

    @Override
    public ObjectMetadata getObject(GetObjectRequest getObjectRequest, File file) 
            throws OSSException, ClientException {
        return objectOperation.getObject(getObjectRequest, file);
    }

    @Override
    public OSSObject getObject(GetObjectRequest getObjectRequest) 
            throws OSSException, ClientException {
        return objectOperation.getObject(getObjectRequest);
    }

    @Override
    public OSSObject getObject(URL signedUrl, Map<String, String> requestHeaders) 
            throws OSSException, ClientException {
        GetObjectRequest getObjectRequest = new GetObjectRequest(signedUrl, requestHeaders);
        return objectOperation.getObject(getObjectRequest);
    }
    
    @Override
    public SimplifiedObjectMeta getSimplifiedObjectMeta(String bucketName, String key)
            throws OSSException, ClientException {
        return this.getSimplifiedObjectMeta(new GenericRequest(bucketName, key));
    }

    @Override
    public SimplifiedObjectMeta getSimplifiedObjectMeta(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return this.objectOperation.getSimplifiedObjectMeta(genericRequest);
    }

    @Override
    public ObjectMetadata getObjectMetadata(String bucketName, String key)
            throws OSSException, ClientException {
        return this.getObjectMetadata(new GenericRequest(bucketName, key));
    }
    
    @Override
    public ObjectMetadata getObjectMetadata(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return objectOperation.getObjectMetadata(genericRequest);
    }
    
    @Override
    public AppendObjectResult appendObject(AppendObjectRequest appendObjectRequest) 
            throws OSSException, ClientException {
        return objectOperation.appendObject(appendObjectRequest);
    }

    @Override
    public void deleteObject(String bucketName, String key) 
            throws OSSException, ClientException {
        this.deleteObject(new GenericRequest(bucketName, key));
    }
    
    @Override
    public void deleteObject(GenericRequest genericRequest)
            throws OSSException, ClientException {
        objectOperation.deleteObject(genericRequest);
    }
    
    @Override
    public DeleteObjectsResult deleteObjects(DeleteObjectsRequest deleteObjectsRequest) 
            throws OSSException, ClientException {
        return objectOperation.deleteObjects(deleteObjectsRequest);
    }
    
    private void headObject(HeadObjectRequest headObjectRequest)
            throws OSSException, ClientException {
        objectOperation.headObject(headObjectRequest);
    }
    
    @Override
    public boolean doesObjectExist(String bucketName, String key)
            throws OSSException, ClientException {
        return doesObjectExist(new HeadObjectRequest(bucketName, key));
    }
    
    @Override
    public boolean doesObjectExist(HeadObjectRequest headObjectRequest)
            throws OSSException, ClientException {
        try {
            headObject(headObjectRequest);
            return true;
        } catch (OSSException e) {
            if (e.getErrorCode() == OSSErrorCode.NO_SUCH_BUCKET 
                    || e.getErrorCode() == OSSErrorCode.NO_SUCH_KEY) {
                return false;
            }
            throw e;
        }
    }
    
    @Override
    public void setObjectAcl(String bucketName, String key, CannedAccessControlList cannedACL) 
            throws OSSException, ClientException {
        this.setObjectAcl(new SetObjectAclRequest(bucketName, key, cannedACL));
    }
    
    @Override
    public void setObjectAcl(SetObjectAclRequest setObjectAclRequest)
            throws OSSException, ClientException {
        objectOperation.setObjectAcl(setObjectAclRequest);
    }

    @Override
    public ObjectAcl getObjectAcl(String bucketName, String key)
            throws OSSException, ClientException {
        return this.getObjectAcl(new GenericRequest(bucketName, key));
    }
    
    @Override
    public ObjectAcl getObjectAcl(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return objectOperation.getObjectAcl(genericRequest);
    }
    
    @Override
    public URL generatePresignedUrl(String bucketName, String key, Date expiration) 
            throws ClientException {
        return generatePresignedUrl(bucketName, key, expiration, HttpMethod.GET);
    }

    @Override
    public URL generatePresignedUrl(String bucketName, String key, Date expiration, HttpMethod method)
            throws ClientException {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, key);
        request.setExpiration(expiration);
        request.setMethod(method);

        return generatePresignedUrl(request);
    }

    @Override
    public URL generatePresignedUrl(GeneratePresignedUrlRequest request) 
            throws ClientException {

        assertParameterNotNull(request, "request");
        
        String bucketName = request.getBucketName();
        if (request.getBucketName() == null) {
            throw new IllegalArgumentException(OSS_RESOURCE_MANAGER.getString("MustSetBucketName"));
        }
        ensureBucketNameValid(request.getBucketName());
        
        if (request.getExpiration() == null) {
            throw new IllegalArgumentException(OSS_RESOURCE_MANAGER.getString("MustSetExpiration"));
        }

        Credentials currentCreds = credsProvider.getCredentials();
        String accessId = currentCreds.getAccessKeyId();
        String accessKey = currentCreds.getSecretAccessKey();
        boolean useSecurityToken = currentCreds.useSecurityToken();
        HttpMethod method = request.getMethod() != null ? request.getMethod() : HttpMethod.GET;

        String expires = String.valueOf(request.getExpiration().getTime() / 1000L);
        String key = request.getKey();
        ClientConfiguration config = serviceClient.getClientConfiguration();
        String resourcePath = OSSUtils.determineResourcePath(bucketName, key, config.isSLDEnabled());

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setEndpoint(OSSUtils.determineFinalEndpoint(endpoint, bucketName, config));
        requestMessage.setMethod(method);
        requestMessage.setResourcePath(resourcePath);
        
        requestMessage.addHeader(HttpHeaders.DATE, expires);
        if (request.getContentType() != null && request.getContentType().trim() != "") {
            requestMessage.addHeader(HttpHeaders.CONTENT_TYPE, request.getContentType());
        }
        if (request.getContentMD5() != null && request.getContentMD5().trim() != "") {
            requestMessage.addHeader(HttpHeaders.CONTENT_MD5, request.getContentMD5());
        }
        for (Map.Entry<String, String> h : request.getUserMetadata().entrySet()) {
            requestMessage.addHeader(OSSHeaders.OSS_USER_METADATA_PREFIX + h.getKey(), h.getValue());
        }
        
        Map<String, String> responseHeaderParams = new HashMap<String, String>();
        populateResponseHeaderParameters(responseHeaderParams, request.getResponseHeaders());
        if (responseHeaderParams.size() > 0) {
            requestMessage.setParameters(responseHeaderParams);
        }

        if (request.getQueryParameter() != null && request.getQueryParameter().size() > 0) {
            for (Map.Entry<String, String> entry : request.getQueryParameter().entrySet()) {
                requestMessage.addParameter(entry.getKey(), entry.getValue());
            }
        }
        
        if (useSecurityToken) {
            requestMessage.addParameter(SECURITY_TOKEN, currentCreds.getSecurityToken());
        }

        String canonicalResource = "/" + ((bucketName != null) ? bucketName : "") 
                + ((key != null ? "/" + key : ""));
        String canonicalString = SignUtils.buildCanonicalString(method.toString(), canonicalResource, 
                requestMessage, expires);
        String signature = ServiceSignature.create().computeSignature(accessKey, canonicalString);

        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put(HttpHeaders.EXPIRES, expires);
        params.put(OSS_ACCESS_KEY_ID, accessId);
        params.put(SIGNATURE, signature);
        params.putAll(requestMessage.getParameters());

        String queryString = HttpUtil.paramToQueryString(params, DEFAULT_CHARSET_NAME);

        /* Compse HTTP request uri. */
        String url = requestMessage.getEndpoint().toString();
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += resourcePath + "?" + queryString;

        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new ClientException(e);
        }
    }

    @Override
    public void abortMultipartUpload(AbortMultipartUploadRequest request) 
            throws OSSException, ClientException {
        multipartOperation.abortMultipartUpload(request);
    }

    @Override
    public CompleteMultipartUploadResult completeMultipartUpload(CompleteMultipartUploadRequest request)
            throws OSSException, ClientException {
        return multipartOperation.completeMultipartUpload(request);
    }

    @Override
    public InitiateMultipartUploadResult initiateMultipartUpload(InitiateMultipartUploadRequest request)
            throws OSSException, ClientException {
        return multipartOperation.initiateMultipartUpload(request);
    }

    @Override
    public MultipartUploadListing listMultipartUploads(ListMultipartUploadsRequest request) 
            throws OSSException, ClientException {
        return multipartOperation.listMultipartUploads(request);
    }

    @Override
    public PartListing listParts(ListPartsRequest request) 
            throws OSSException, ClientException {
        return multipartOperation.listParts(request);
    }

    @Override
    public UploadPartResult uploadPart(UploadPartRequest request) 
            throws OSSException, ClientException {
        return multipartOperation.uploadPart(request);
    }
    
    @Override
    public UploadPartCopyResult uploadPartCopy(UploadPartCopyRequest request) 
            throws OSSException, ClientException {
        return multipartOperation.uploadPartCopy(request);
    }

    @Override
    public void setBucketCORS(SetBucketCORSRequest request) 
            throws OSSException, ClientException {
        corsOperation.setBucketCORS(request);
    }

    @Override
    public List<CORSRule> getBucketCORSRules(String bucketName) 
            throws OSSException, ClientException {
        return this.getBucketCORSRules(new GenericRequest(bucketName));
    }
    
    @Override
    public List<CORSRule> getBucketCORSRules(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return corsOperation.getBucketCORSRules(genericRequest);
    }

    @Override
    public void deleteBucketCORSRules(String bucketName) 
            throws OSSException, ClientException {
        this.deleteBucketCORSRules(new GenericRequest(bucketName));
    }
    
    @Override
    public void deleteBucketCORSRules(GenericRequest genericRequest)
            throws OSSException, ClientException {
        corsOperation.deleteBucketCORS(genericRequest);
    }

    @Override
    public ResponseMessage optionsObject(OptionsRequest request) 
            throws OSSException, ClientException {
        return corsOperation.optionsObject(request);
    }
    
    @Override
    public void setBucketLogging(SetBucketLoggingRequest request) 
            throws OSSException, ClientException {
         bucketOperation.setBucketLogging(request);
    }
    
    @Override
    public BucketLoggingResult getBucketLogging(String bucketName)
            throws OSSException, ClientException {
        return this.getBucketLogging(new GenericRequest(bucketName));
    }
    
    @Override
    public BucketLoggingResult getBucketLogging(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return bucketOperation.getBucketLogging(genericRequest);
    }
    
    @Override
    public void deleteBucketLogging(String bucketName) 
            throws OSSException, ClientException {
        this.deleteBucketLogging(new GenericRequest(bucketName));
    }
    
    @Override
    public void deleteBucketLogging(GenericRequest genericRequest)
            throws OSSException, ClientException {
        bucketOperation.deleteBucketLogging(genericRequest);
    }
    @Override
	public void putBucketImage(PutBucketImageRequest request)
	    		throws OSSException, ClientException{
		bucketOperation.putBucketImage(request);
	}
    
	@Override
	public GetBucketImageResult getBucketImage(String bucketName) 
			throws OSSException, ClientException{
		return bucketOperation.getBucketImage(bucketName, new GenericRequest());
	}
	
	@Override
	public GetBucketImageResult getBucketImage(String bucketName, GenericRequest genericRequest) 
			throws OSSException, ClientException{
		return bucketOperation.getBucketImage(bucketName, genericRequest);
	}
	
	@Override
	public void deleteBucketImage(String bucketName) 
			throws OSSException, ClientException{
		bucketOperation.deleteBucketImage(bucketName, new GenericRequest());
	}
	
	@Override
	public void deleteBucketImage(String bucketName, GenericRequest genericRequest) 
			throws OSSException, ClientException{
		bucketOperation.deleteBucketImage(bucketName, genericRequest);
	}
	
	@Override
	public void putImageStyle(PutImageStyleRequest putImageStyleRequest)
			throws OSSException, ClientException{
		bucketOperation.putImageStyle(putImageStyleRequest);
	}
	
	@Override
	public void deleteImageStyle(String bucketName, String styleName)
			throws OSSException, ClientException{
		bucketOperation.deleteImageStyle(bucketName, styleName, new GenericRequest());
	}
	
	@Override
	public void deleteImageStyle(String bucketName, String styleName, GenericRequest genericRequest)
			throws OSSException, ClientException{
		bucketOperation.deleteImageStyle(bucketName, styleName, genericRequest);
	}
	
	@Override
	public GetImageStyleResult getImageStyle(String bucketName, String styleName)
    		throws OSSException, ClientException{
		return bucketOperation.getImageStyle(bucketName, styleName, new GenericRequest());
	}
	
	@Override
	public GetImageStyleResult getImageStyle(String bucketName, String styleName, GenericRequest genericRequest)
    		throws OSSException, ClientException{
		return bucketOperation.getImageStyle(bucketName, styleName, genericRequest);
	}
	
	@Override
    public List<Style> listImageStyle(String bucketName) 
    		throws OSSException, ClientException {
            return bucketOperation.listImageStyle(bucketName, new GenericRequest());
    }
	
	@Override
    public List<Style> listImageStyle(String bucketName, GenericRequest genericRequest) 
    		throws OSSException, ClientException {
            return bucketOperation.listImageStyle(bucketName, genericRequest);
    }

    @Override
    public void setBucketWebsite(SetBucketWebsiteRequest setBucketWebSiteRequest)
            throws OSSException, ClientException {
        bucketOperation.setBucketWebsite(setBucketWebSiteRequest);
    }

    @Override
    public BucketWebsiteResult getBucketWebsite(String bucketName)
            throws OSSException, ClientException {
        return this.getBucketWebsite(new GenericRequest(bucketName));
    }
    
    @Override
    public BucketWebsiteResult getBucketWebsite(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return bucketOperation.getBucketWebsite(genericRequest);
    }

    @Override
    public void deleteBucketWebsite(String bucketName) 
            throws OSSException, ClientException {
        this.deleteBucketWebsite(new GenericRequest(bucketName));
    }
    
    @Override
    public void deleteBucketWebsite(GenericRequest genericRequest)
            throws OSSException, ClientException {
        bucketOperation.deleteBucketWebsite(genericRequest);
    }
    
    @Override
    public String generatePostPolicy(Date expiration, PolicyConditions conds) {
        String formatedExpiration = DateUtil.formatIso8601Date(expiration);
        String jsonizedExpiration = String.format("\"expiration\":\"%s\"", formatedExpiration);
        String jsonizedConds = conds.jsonize();
        
        StringBuilder postPolicy = new StringBuilder();
        postPolicy.append(String.format("{%s,%s}", jsonizedExpiration, jsonizedConds));

        return postPolicy.toString();
    }
    
    @Override
    public String calculatePostSignature(String postPolicy) throws ClientException {
        try {
            byte[] binaryData = postPolicy.getBytes(DEFAULT_CHARSET_NAME);
            String encPolicy = BinaryUtil.toBase64String(binaryData);
            return ServiceSignature.create().computeSignature(
                    credsProvider.getCredentials().getSecretAccessKey(), encPolicy);
        } catch (UnsupportedEncodingException ex) {
            throw new ClientException("Unsupported charset: " + ex.getMessage());
        }
    }
    
    @Override
    public void setBucketLifecycle(SetBucketLifecycleRequest setBucketLifecycleRequest)
            throws OSSException, ClientException {
        bucketOperation.setBucketLifecycle(setBucketLifecycleRequest);
    }
    
    @Override
    public List<LifecycleRule> getBucketLifecycle(String bucketName)
            throws OSSException, ClientException {
        return this.getBucketLifecycle(new GenericRequest(bucketName));
    }
    
    @Override
    public List<LifecycleRule> getBucketLifecycle(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return bucketOperation.getBucketLifecycle(genericRequest);
    }

    @Override
    public void deleteBucketLifecycle(String bucketName)
            throws OSSException, ClientException {
        this.deleteBucketLifecycle(new GenericRequest(bucketName));
    }

    @Override
    public void deleteBucketLifecycle(GenericRequest genericRequest)
            throws OSSException, ClientException {
        bucketOperation.deleteBucketLifecycle(genericRequest);
    }
    
    @Override
    public void setBucketTagging(String bucketName, Map<String, String> tags)
            throws OSSException, ClientException {
        this.setBucketTagging(new SetBucketTaggingRequest(bucketName, tags));
    }
    

    @Override
    public void setBucketTagging(String bucketName, TagSet tagSet)
            throws OSSException, ClientException {
        this.setBucketTagging(new SetBucketTaggingRequest(bucketName, tagSet));
    }

    @Override
    public void setBucketTagging(SetBucketTaggingRequest setBucketTaggingRequest)
            throws OSSException, ClientException {
        this.bucketOperation.setBucketTagging(setBucketTaggingRequest);
    }

    @Override
    public TagSet getBucketTagging(String bucketName) 
            throws OSSException, ClientException {
        return this.getBucketTagging(new GenericRequest(bucketName));
    }

    @Override
    public TagSet getBucketTagging(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return this.bucketOperation.getBucketTagging(genericRequest);
    }

    @Override
    public void deleteBucketTagging(String bucketName) 
            throws OSSException, ClientException {
        this.deleteBucketTagging(new GenericRequest(bucketName));
    }

    @Override
    public void deleteBucketTagging(GenericRequest genericRequest)
            throws OSSException, ClientException {
        this.bucketOperation.deleteBucketTagging(genericRequest);
    }
    
    @Override
    public void addBucketReplication(AddBucketReplicationRequest addBucketReplicationRequest)
            throws OSSException, ClientException {
        this.bucketOperation.addBucketReplication(addBucketReplicationRequest);
    }

    @Override
    public List<ReplicationRule> getBucketReplication(String bucketName)
            throws OSSException, ClientException {
        return this.getBucketReplication(new GenericRequest(bucketName));
    }

    @Override
    public List<ReplicationRule> getBucketReplication(GenericRequest genericRequest) 
            throws OSSException, ClientException {
        return this.bucketOperation.getBucketReplication(genericRequest);
    }

    @Override
    public void deleteBucketReplication(String bucketName,
            String replicationRuleID) throws OSSException, ClientException {
        this.deleteBucketReplication(new DeleteBucketReplicationRequest(
                bucketName, replicationRuleID));
    }

    @Override
    public void deleteBucketReplication(
            DeleteBucketReplicationRequest deleteBucketReplicationRequest)
            throws OSSException, ClientException {
        this.bucketOperation.deleteBucketReplication(deleteBucketReplicationRequest);
    }

    @Override
    public BucketReplicationProgress getBucketReplicationProgress(
            String bucketName, String replicationRuleID) throws OSSException,
            ClientException {
        return this.getBucketReplicationProgress(new GetBucketReplicationProgressRequest(
                        bucketName, replicationRuleID));
    }

    @Override
    public BucketReplicationProgress getBucketReplicationProgress(
            GetBucketReplicationProgressRequest getBucketReplicationProgressRequest)
            throws OSSException, ClientException {
        return this.bucketOperation.getBucketReplicationProgress(getBucketReplicationProgressRequest);
    }

    @Override
    public List<String> getBucketReplicationLocation(String bucketName)
            throws OSSException, ClientException {
        return this.getBucketReplicationLocation(new GenericRequest(bucketName));
    }

    @Override
    public List<String> getBucketReplicationLocation(GenericRequest genericRequest) 
            throws OSSException, ClientException {
        return this.bucketOperation.getBucketReplicationLocation(genericRequest);
    }

    @Override
    public void addBucketCname(AddBucketCnameRequest addBucketCnameRequest)
            throws OSSException, ClientException {
        this.bucketOperation.addBucketCname(addBucketCnameRequest);
    }

    @Override
    public List<CnameConfiguration> getBucketCname(String bucketName)
            throws OSSException, ClientException {
        return this.getBucketCname(new GenericRequest(bucketName));
    }

    @Override
    public List<CnameConfiguration> getBucketCname(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return this.bucketOperation.getBucketCname(genericRequest);
    }

    @Override
    public void deleteBucketCname(String bucketName, String domain)
            throws OSSException, ClientException {
        this.deleteBucketCname(new DeleteBucketCnameRequest(bucketName, domain));
    }

    @Override
    public void deleteBucketCname(DeleteBucketCnameRequest deleteBucketCnameRequest)
            throws OSSException, ClientException {
        this.bucketOperation.deleteBucketCname(deleteBucketCnameRequest);
    }
    
    @Override
    public BucketInfo getBucketInfo(String bucketName) throws OSSException,
            ClientException {
        return this.getBucketInfo(new GenericRequest(bucketName));
    }

    @Override
    public BucketInfo getBucketInfo(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return this.bucketOperation.getBucketInfo(genericRequest);
    }
    
    @Override
    public void setBucketStorageCapacity(String bucketName, UserQos userQos) throws OSSException,
            ClientException {
        this.setBucketStorageCapacity(new SetBucketStorageCapacityRequest(bucketName).withUserQos(userQos)); 
    }

    @Override
    public void setBucketStorageCapacity(SetBucketStorageCapacityRequest setBucketStorageCapacityRequest)
            throws OSSException, ClientException {
        this.bucketOperation.setBucketStorageCapacity(setBucketStorageCapacityRequest);
    }

    @Override
    public UserQos getBucketStorageCapacity(String bucketName)
            throws OSSException, ClientException {
        return this.getBucketStorageCapacity(new GenericRequest(bucketName));
    }

    @Override
    public UserQos getBucketStorageCapacity(GenericRequest genericRequest)
            throws OSSException, ClientException {
        return this.bucketOperation.getBucketStorageCapacity(genericRequest);
    }
	
	@Override
    public UploadFileResult uploadFile(UploadFileRequest uploadFileRequest) throws Throwable {
        return this.uploadOperation.uploadFile(uploadFileRequest);
    }
    
    @Override
    public DownloadFileResult downloadFile(DownloadFileRequest downloadFileRequest) throws Throwable {
        return downloadOperation.downloadFile(downloadFileRequest);
    }
    
    @Override
    public CreateLiveChannelResult createLiveChannel(CreateLiveChannelRequest createLiveChannelRequest) 
            throws OSSException, ClientException {
        return liveChannelOperation.createLiveChannel(createLiveChannelRequest);
    }
    
    @Override
    public void setLiveChannelStatus(String bucketName, String liveChannel, LiveChannelStatus status) 
            throws OSSException, ClientException {
        this.setLiveChannelStatus(new SetLiveChannelRequest(bucketName, liveChannel, status));
    }
    
    @Override
    public void setLiveChannelStatus(SetLiveChannelRequest setLiveChannelRequest) 
            throws OSSException, ClientException {
        liveChannelOperation.setLiveChannelStatus(setLiveChannelRequest);
    }
    
    @Override
    public LiveChannelInfo getLiveChannelInfo(String bucketName, String liveChannel) 
            throws OSSException, ClientException {
        return this.getLiveChannelInfo(new LiveChannelGenericRequest(bucketName, liveChannel));
    }
    
    @Override
    public LiveChannelInfo getLiveChannelInfo(LiveChannelGenericRequest liveChannelGenericRequest) 
            throws OSSException, ClientException {
        return liveChannelOperation.getLiveChannelInfo(liveChannelGenericRequest);
    }
    
    @Override
    public LiveChannelStat getLiveChannelStat(String bucketName, String liveChannel) 
            throws OSSException, ClientException {
        return this.getLiveChannelStat(new LiveChannelGenericRequest(bucketName, liveChannel));
    }
    
    @Override
    public LiveChannelStat getLiveChannelStat(LiveChannelGenericRequest liveChannelGenericRequest) 
            throws OSSException, ClientException {
        return liveChannelOperation.getLiveChannelStat(liveChannelGenericRequest);
    }
    
    @Override
    public void deleteLiveChannel(String bucketName, String liveChannel) 
            throws OSSException, ClientException {
        this.deleteLiveChannel(new LiveChannelGenericRequest(bucketName, liveChannel));
    }
    
    @Override
    public void deleteLiveChannel(LiveChannelGenericRequest liveChannelGenericRequest) 
            throws OSSException, ClientException {
        liveChannelOperation.deleteLiveChannel(liveChannelGenericRequest);
    }
    
    @Override
    public List<LiveChannel> listLiveChannels(String bucketName) throws OSSException, ClientException {
        return liveChannelOperation.listLiveChannels(bucketName);
    }
    
    @Override
    public LiveChannelListing listLiveChannels(ListLiveChannelsRequest listLiveChannelRequest) 
            throws OSSException, ClientException {
        return liveChannelOperation.listLiveChannels(listLiveChannelRequest);
    }
    
    @Override
    public List<LiveRecord> getLiveChannelHistory(String bucketName, String liveChannel) 
            throws OSSException, ClientException {
        return this.getLiveChannelHistory(new LiveChannelGenericRequest(bucketName, liveChannel));
    }
    
    @Override
    public List<LiveRecord> getLiveChannelHistory(LiveChannelGenericRequest liveChannelGenericRequest) 
            throws OSSException, ClientException {
        return liveChannelOperation.getLiveChannelHistory(liveChannelGenericRequest);
    }
    
    @Override
    public void GenerateVodPlaylist(String bucketName, String liveChannelName, String PlaylistName,
            long startTime, long endTime) throws OSSException, ClientException {
        this.GenerateVodPlaylist(new GenerateVodPlaylistRequest(bucketName, liveChannelName,
                PlaylistName, startTime, endTime));
    }
    
    @Override
    public void GenerateVodPlaylist(GenerateVodPlaylistRequest generateVodPlaylistRequest) 
            throws OSSException, ClientException {
        liveChannelOperation.GenerateVodPlaylist(generateVodPlaylistRequest);
    }
   
    @Override
    public String GenerateRtmpUri(String bucketName, String liveChannelName, String PlaylistName,
            long expires) throws OSSException, ClientException {
        return this.GenerateRtmpUri(new GenerateRtmpUriRequest(bucketName, liveChannelName,
                PlaylistName, expires, null));
    }
    
    @Override
    public String GenerateRtmpUri(String bucketName, String liveChannelName, String PlaylistName,
            long expires, Map<String, String> parameters) throws OSSException, ClientException {
        return this.GenerateRtmpUri(new GenerateRtmpUriRequest(bucketName, liveChannelName,
                PlaylistName, expires, parameters));
    }
    
    @Override
    public String GenerateRtmpUri(GenerateRtmpUriRequest generatePushflowUrlRequest) 
            throws OSSException, ClientException {
        return liveChannelOperation.GeneratePushflowUrl(generatePushflowUrlRequest);
    }
    
    @Override
    public void shutdown() {
        serviceClient.shutdown();
    }
    
}
