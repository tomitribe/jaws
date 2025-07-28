/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2025
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package software.amazon.nio.spi.s3;

import software.amazon.nio.spi.s3.util.S3FileSystemInfo;

import java.net.URI;
import java.util.Objects;

/** A custom S3 file system provider for Harminie, extending the base S3FileSystemProvider.
 * This provider is designed to work with the Harminie system and provides specific
 * implementations for handling S3 URIs in the context of Harminie.
 *
 * As opposed to Trixie, Pixie does not have a singleton we can use to get configuration
 * or components, we need to use the System component to get the S3HarminieFilesystem because
 * we need to access the AmazonAccount.
 * There is a small trick here, and I'm not happy about it, but it works. We can refactor that.
 * The main goal was to abstract the S3 usage using the standard Java NIO API.
 *
 * IMPORTANT: this class is part of the SPI (Service Provider Interface) for S3 file systems
 * It is in the `software.amazon.nio.spi.s3` package to indicate that it is a service provider
 * and also because fileSystemInfo() is package-private in S3FileSystemProvider.
 *
 * We should not use this class directly and rewrite the base class to use the AWS Provider API
 */
public class S3HarminieFileSystemProvider extends S3FileSystemProvider {

    public static final String SCHEME = "s3harminie";

    @Override
    public String getScheme() {
        return SCHEME;
    }

    @Override
    S3FileSystemInfo fileSystemInfo(final URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");

        final S3HarminieFilesystem s3HarminieFilesystem = new S3HarminieFilesystem("foo", "bar");
        return s3HarminieFilesystem.asS3FireSystemInfo(uri);
    }

    public static class S3HarminieFileSystemInfo extends S3FileSystemInfo {

        public S3HarminieFileSystemInfo(final String accessKey, final String accessSecret, final URI uri) {
            Objects.requireNonNull(uri, "uri must not be null");
            Objects.requireNonNull(accessKey, "accessKey must not be null");
            Objects.requireNonNull(accessSecret, "accessSecret must not be null");

            final String[] pathParts = uri.getPath().split("/");
            if (pathParts.length == 0) { // this is only the bucket name
                super.bucket = uri.getHost();
                super.key = uri.getHost(); // no key, just the bucket name
                super.accessKey = accessKey;
                super.accessSecret = accessSecret;

            } else { // this is a full S3 URI with bucket and key
                final String userInfo = uri.getUserInfo();

                if (userInfo != null) {
                    int pos = userInfo.indexOf(':');
                    super.accessKey = (pos < 0) ? userInfo : userInfo.substring(0, pos);
                    super.accessSecret = (pos < 0) ? null : userInfo.substring(pos + 1);
                }

                super.endpoint = uri.getHost();
                if (uri.getPort() > 0) {
                    super.endpoint += ":" + uri.getPort();
                }
                super.bucket = pathParts[1];

                super.key = super.endpoint + '/' + super.bucket;
                if (super.accessKey != null) {
                    super.key = super.accessKey + '@' + super.key;
                }

            }
        }

    }

    public static class S3HarminieFilesystem {

        private final String accessKey;
        private final String secretKey;

        public S3HarminieFilesystem(final String accessKey, final String secretKey) {
            this.accessKey = accessKey;
            this.secretKey = secretKey;
            Objects.requireNonNull(accessKey, "accessKey must not be null");
            Objects.requireNonNull(secretKey, "secretKey must not be null");



        }

        public S3FileSystemInfo asS3FireSystemInfo(final URI uri) {
            return new S3HarminieFileSystemInfo(accessKey, secretKey, uri);
        }
    }

}