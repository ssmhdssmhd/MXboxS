package org.gradle.accessors.dm;

import org.jspecify.annotations.NullMarked;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.plugin.use.PluginDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.internal.catalog.AbstractExternalDependencyFactory;
import org.gradle.api.internal.catalog.DefaultVersionCatalog;
import java.util.Map;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import javax.inject.Inject;
import org.gradle.api.GradleException;

/**
 * A catalog of dependencies accessible via the {@code libs} extension.
 */
@NullMarked
public class LibrariesForLibsInPluginsBlock extends AbstractExternalDependencyFactory {

    private final AbstractExternalDependencyFactory owner = this;
    private final AndroidLibraryAccessors laccForAndroidLibraryAccessors = new AndroidLibraryAccessors(owner);
    private final EventbusLibraryAccessors laccForEventbusLibraryAccessors = new EventbusLibraryAccessors(owner);
    private final GlideLibraryAccessors laccForGlideLibraryAccessors = new GlideLibraryAccessors(owner);
    private final LifecycleLibraryAccessors laccForLifecycleLibraryAccessors = new LifecycleLibraryAccessors(owner);
    private final OkhttpLibraryAccessors laccForOkhttpLibraryAccessors = new OkhttpLibraryAccessors(owner);
    private final OrgLibraryAccessors laccForOrgLibraryAccessors = new OrgLibraryAccessors(owner);
    private final QuickjsLibraryAccessors laccForQuickjsLibraryAccessors = new QuickjsLibraryAccessors(owner);
    private final RoomLibraryAccessors laccForRoomLibraryAccessors = new RoomLibraryAccessors(owner);
    private final RtmpLibraryAccessors laccForRtmpLibraryAccessors = new RtmpLibraryAccessors(owner);
    private final SimpleLibraryAccessors laccForSimpleLibraryAccessors = new SimpleLibraryAccessors(owner);
    private final ZxingLibraryAccessors laccForZxingLibraryAccessors = new ZxingLibraryAccessors(owner);
    private final VersionAccessors vaccForVersionAccessors = new VersionAccessors(providers, config);
    private final BundleAccessors baccForBundleAccessors = new BundleAccessors(objects, providers, config, attributesFactory, capabilityNotationParser);
    private final PluginAccessors paccForPluginAccessors = new PluginAccessors(providers, config);

    @Inject
    public LibrariesForLibsInPluginsBlock(DefaultVersionCatalog config, ProviderFactory providers, ObjectFactory objects, AttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) {
        super(config, providers, objects, attributesFactory, capabilityNotationParser);
    }

    /**
     * Dependency provider for <b>androidautosize</b> with <b>com.github.JessYanCoding:AndroidAutoSize</b> coordinates and
     * with version reference <b>androidautosize</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getAndroidautosize() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>annotation</b> with <b>androidx.annotation:annotation</b> coordinates and
     * with version reference <b>annotation</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getAnnotation() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>appcompat</b> with <b>androidx.appcompat:appcompat</b> coordinates and
     * with version reference <b>appcompat</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getAppcompat() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>biometric</b> with <b>androidx.biometric:biometric</b> coordinates and
     * with version reference <b>biometric</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getBiometric() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>brotli</b> with <b>org.brotli:dec</b> coordinates and
     * with version reference <b>brotli</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getBrotli() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>customactivityoncrash</b> with <b>cat.ereza:customactivityoncrash</b> coordinates and
     * with version reference <b>customactivityoncrash</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getCustomactivityoncrash() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>desugar</b> with <b>com.android.tools:desugar_jdk_libs_nio</b> coordinates and
     * with version reference <b>desugar</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getDesugar() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>flexbox</b> with <b>com.google.android.flexbox:flexbox</b> coordinates and
     * with version reference <b>flexbox</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getFlexbox() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>gson</b> with <b>com.google.code.gson:gson</b> coordinates and
     * with version reference <b>gson</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getGson() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>guava</b> with <b>com.google.guava:guava</b> coordinates and
     * with version reference <b>guava</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getGuava() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>jna</b> with <b>net.java.dev.jna:jna</b> coordinates and
     * with version reference <b>jna</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getJna() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>juniversalchardet</b> with <b>com.googlecode.juniversalchardet:juniversalchardet</b> coordinates and
     * with version reference <b>juniversalchardet</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getJuniversalchardet() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>leanback</b> with <b>androidx.leanback:leanback</b> coordinates and
     * with version reference <b>leanback</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getLeanback() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>logger</b> with <b>com.orhanobut:logger</b> coordinates and
     * with version reference <b>logger</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getLogger() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>lottie</b> with <b>com.airbnb.android:lottie</b> coordinates and
     * with version reference <b>lottie</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getLottie() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>material</b> with <b>com.google.android.material:material</b> coordinates and
     * with version reference <b>material</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getMaterial() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>materialdesigncolors</b> with <b>com.github.bassaer:materialdesigncolors</b> coordinates and
     * with version reference <b>materialdesigncolors</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getMaterialdesigncolors() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>media</b> with <b>androidx.media:media</b> coordinates and
     * with version reference <b>media</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getMedia() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>nanohttpd</b> with <b>org.nanohttpd:nanohttpd</b> coordinates and
     * with version reference <b>nanohttpd</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getNanohttpd() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>newpipeextractor</b> with <b>com.github.TeamNewPipe:NewPipeExtractor</b> coordinates and
     * with version reference <b>newpipeextractor</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getNewpipeextractor() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>palette</b> with <b>androidx.palette:palette</b> coordinates and
     * with version reference <b>palette</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getPalette() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>permissionx</b> with <b>com.guolindev.permissionx:permissionx</b> coordinates and
     * with version reference <b>permissionx</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getPermissionx() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>preference</b> with <b>androidx.preference:preference</b> coordinates and
     * with version reference <b>preference</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getPreference() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>recyclerview</b> with <b>androidx.recyclerview:recyclerview</b> coordinates and
     * with version reference <b>recyclerview</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getRecyclerview() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>sardine</b> with <b>com.github.thegrizzlylabs:sardine-android</b> coordinates and
     * with version reference <b>sardine</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getSardine() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>smbj</b> with <b>com.hierynomus:smbj</b> coordinates and
     * with version reference <b>smbj</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getSmbj() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>splashscreen</b> with <b>androidx.core:core-splashscreen</b> coordinates and
     * with version reference <b>splashscreen</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getSplashscreen() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>startup</b> with <b>androidx.startup:startup-runtime</b> coordinates and
     * with version reference <b>startup</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getStartup() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>swiperefreshlayout</b> with <b>androidx.swiperefreshlayout:swiperefreshlayout</b> coordinates and
     * with version reference <b>swiperefreshlayout</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getSwiperefreshlayout() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>textdrawable</b> with <b>com.github.jahirfiquitiva:TextDrawable</b> coordinates and
     * with version reference <b>textdrawable</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getTextdrawable() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Dependency provider for <b>viewpager2</b> with <b>androidx.viewpager2:viewpager2</b> coordinates and
     * with version reference <b>viewpager2</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getViewpager2() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of libraries at <b>android</b>
     */
    public AndroidLibraryAccessors getAndroid() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of libraries at <b>eventbus</b>
     */
    public EventbusLibraryAccessors getEventbus() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of libraries at <b>glide</b>
     */
    public GlideLibraryAccessors getGlide() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of libraries at <b>lifecycle</b>
     */
    public LifecycleLibraryAccessors getLifecycle() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of libraries at <b>okhttp</b>
     */
    public OkhttpLibraryAccessors getOkhttp() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of libraries at <b>org</b>
     */
    public OrgLibraryAccessors getOrg() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of libraries at <b>quickjs</b>
     */
    public QuickjsLibraryAccessors getQuickjs() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of libraries at <b>room</b>
     */
    public RoomLibraryAccessors getRoom() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of libraries at <b>rtmp</b>
     */
    public RtmpLibraryAccessors getRtmp() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of libraries at <b>simple</b>
     */
    public SimpleLibraryAccessors getSimple() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of libraries at <b>zxing</b>
     */
    public ZxingLibraryAccessors getZxing() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of versions at <b>versions</b>
     */
    public VersionAccessors getVersions() {
        return vaccForVersionAccessors;
    }

    /**
     * Group of bundles at <b>bundles</b>
     */
    public BundleAccessors getBundles() {
        throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
    }

    /**
     * Group of plugins at <b>plugins</b>
     */
    public PluginAccessors getPlugins() {
        return paccForPluginAccessors;
    }

    public static class AndroidLibraryAccessors extends SubDependencyFactory {
        private final AndroidGifLibraryAccessors laccForAndroidGifLibraryAccessors = new AndroidGifLibraryAccessors(owner);

        public AndroidLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Group of libraries at <b>android.gif</b>
         */
        public AndroidGifLibraryAccessors getGif() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class AndroidGifLibraryAccessors extends SubDependencyFactory {

        public AndroidGifLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>drawable</b> with <b>pl.droidsonroids.gif:android-gif-drawable</b> coordinates and
         * with version reference <b>androidGifDrawable</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getDrawable() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class EventbusLibraryAccessors extends SubDependencyFactory implements DependencyNotationSupplier {
        private final EventbusAnnotationLibraryAccessors laccForEventbusAnnotationLibraryAccessors = new EventbusAnnotationLibraryAccessors(owner);

        public EventbusLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>eventbus</b> with <b>org.greenrobot:eventbus</b> coordinates and
         * with version reference <b>greenrobot</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> asProvider() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Group of libraries at <b>eventbus.annotation</b>
         */
        public EventbusAnnotationLibraryAccessors getAnnotation() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class EventbusAnnotationLibraryAccessors extends SubDependencyFactory {

        public EventbusAnnotationLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>processor</b> with <b>org.greenrobot:eventbus-annotation-processor</b> coordinates and
         * with version reference <b>greenrobot</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getProcessor() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class GlideLibraryAccessors extends SubDependencyFactory implements DependencyNotationSupplier {
        private final GlideAvifLibraryAccessors laccForGlideAvifLibraryAccessors = new GlideAvifLibraryAccessors(owner);
        private final GlideOkhttp3LibraryAccessors laccForGlideOkhttp3LibraryAccessors = new GlideOkhttp3LibraryAccessors(owner);

        public GlideLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>glide</b> with <b>com.github.bumptech.glide:glide</b> coordinates and
         * with version reference <b>glide</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> asProvider() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Dependency provider for <b>annotations</b> with <b>com.github.bumptech.glide:annotations</b> coordinates and
         * with version reference <b>glide</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getAnnotations() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Dependency provider for <b>compiler</b> with <b>com.github.bumptech.glide:compiler</b> coordinates and
         * with version reference <b>glide</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCompiler() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Group of libraries at <b>glide.avif</b>
         */
        public GlideAvifLibraryAccessors getAvif() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Group of libraries at <b>glide.okhttp3</b>
         */
        public GlideOkhttp3LibraryAccessors getOkhttp3() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class GlideAvifLibraryAccessors extends SubDependencyFactory {

        public GlideAvifLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>integration</b> with <b>com.github.bumptech.glide:avif-integration</b> coordinates and
         * with version reference <b>glide</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getIntegration() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class GlideOkhttp3LibraryAccessors extends SubDependencyFactory {

        public GlideOkhttp3LibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>integration</b> with <b>com.github.bumptech.glide:okhttp3-integration</b> coordinates and
         * with version reference <b>glide</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getIntegration() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class LifecycleLibraryAccessors extends SubDependencyFactory {

        public LifecycleLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>service</b> with <b>androidx.lifecycle:lifecycle-service</b> coordinates and
         * with version reference <b>lifecycleService</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getService() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class OkhttpLibraryAccessors extends SubDependencyFactory implements DependencyNotationSupplier {
        private final OkhttpLoggingLibraryAccessors laccForOkhttpLoggingLibraryAccessors = new OkhttpLoggingLibraryAccessors(owner);

        public OkhttpLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>okhttp</b> with <b>com.squareup.okhttp3:okhttp</b> coordinates and
         * with version reference <b>okhttp</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> asProvider() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Dependency provider for <b>dnsoverhttps</b> with <b>com.squareup.okhttp3:okhttp-dnsoverhttps</b> coordinates and
         * with version reference <b>okhttp</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getDnsoverhttps() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Group of libraries at <b>okhttp.logging</b>
         */
        public OkhttpLoggingLibraryAccessors getLogging() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class OkhttpLoggingLibraryAccessors extends SubDependencyFactory {

        public OkhttpLoggingLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>interceptor</b> with <b>com.squareup.okhttp3:logging-interceptor</b> coordinates and
         * with version reference <b>okhttp</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getInterceptor() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class OrgLibraryAccessors extends SubDependencyFactory {
        private final OrgJupnpLibraryAccessors laccForOrgJupnpLibraryAccessors = new OrgJupnpLibraryAccessors(owner);

        public OrgLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Group of libraries at <b>org.jupnp</b>
         */
        public OrgJupnpLibraryAccessors getJupnp() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class OrgJupnpLibraryAccessors extends SubDependencyFactory implements DependencyNotationSupplier {

        public OrgJupnpLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>jupnp</b> with <b>org.jupnp:org.jupnp</b> coordinates and
         * with version reference <b>jupnp</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> asProvider() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Dependency provider for <b>android</b> with <b>org.jupnp:org.jupnp.android</b> coordinates and
         * with version reference <b>jupnp</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getAndroid() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Dependency provider for <b>support</b> with <b>org.jupnp:org.jupnp.support</b> coordinates and
         * with version reference <b>jupnp</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getSupport() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class QuickjsLibraryAccessors extends SubDependencyFactory {

        public QuickjsLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>android</b> with <b>wang.harlon.quickjs:wrapper-android</b> coordinates and
         * with version reference <b>quickjs</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getAndroid() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Dependency provider for <b>java</b> with <b>wang.harlon.quickjs:wrapper-java</b> coordinates and
         * with version reference <b>quickjs</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getJava() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class RoomLibraryAccessors extends SubDependencyFactory {

        public RoomLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>compiler</b> with <b>androidx.room:room-compiler</b> coordinates and
         * with version reference <b>room</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCompiler() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Dependency provider for <b>runtime</b> with <b>androidx.room:room-runtime</b> coordinates and
         * with version reference <b>room</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getRuntime() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class RtmpLibraryAccessors extends SubDependencyFactory {

        public RtmpLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>client</b> with <b>com.github.mcxinyu:LibRtmp-Client-for-Android</b> coordinates and
         * with version reference <b>rtmpClient</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getClient() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class SimpleLibraryAccessors extends SubDependencyFactory {

        public SimpleLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>xml</b> with <b>org.simpleframework:simple-xml</b> coordinates and
         * with version reference <b>simpleXml</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getXml() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class ZxingLibraryAccessors extends SubDependencyFactory {
        private final ZxingAndroidLibraryAccessors laccForZxingAndroidLibraryAccessors = new ZxingAndroidLibraryAccessors(owner);

        public ZxingLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>core</b> with <b>com.google.zxing:core</b> coordinates and
         * with version reference <b>zxingCore</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCore() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Group of libraries at <b>zxing.android</b>
         */
        public ZxingAndroidLibraryAccessors getAndroid() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class ZxingAndroidLibraryAccessors extends SubDependencyFactory {

        public ZxingAndroidLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>embedded</b> with <b>com.journeyapps:zxing-android-embedded</b> coordinates and
         * with version reference <b>zxingAndroidEmbedded</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getEmbedded() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class VersionAccessors extends VersionFactory  {

        public VersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Version alias <b>agp</b> with value <b>9.2.1</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getAgp() { return getVersion("agp"); }

        /**
         * Version alias <b>androidGifDrawable</b> with value <b>1.2.32</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getAndroidGifDrawable() { return getVersion("androidGifDrawable"); }

        /**
         * Version alias <b>androidautosize</b> with value <b>1.2.1</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getAndroidautosize() { return getVersion("androidautosize"); }

        /**
         * Version alias <b>annotation</b> with value <b>1.10.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getAnnotation() { return getVersion("annotation"); }

        /**
         * Version alias <b>appcompat</b> with value <b>1.7.1</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getAppcompat() { return getVersion("appcompat"); }

        /**
         * Version alias <b>biometric</b> with value <b>1.1.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getBiometric() { return getVersion("biometric"); }

        /**
         * Version alias <b>brotli</b> with value <b>0.1.2</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getBrotli() { return getVersion("brotli"); }

        /**
         * Version alias <b>compileSdk</b> with value <b>37</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getCompileSdk() { return getVersion("compileSdk"); }

        /**
         * Version alias <b>customactivityoncrash</b> with value <b>2.4.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getCustomactivityoncrash() { return getVersion("customactivityoncrash"); }

        /**
         * Version alias <b>desugar</b> with value <b>2.1.5</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getDesugar() { return getVersion("desugar"); }

        /**
         * Version alias <b>flexbox</b> with value <b>3.0.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getFlexbox() { return getVersion("flexbox"); }

        /**
         * Version alias <b>glide</b> with value <b>5.0.7</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getGlide() { return getVersion("glide"); }

        /**
         * Version alias <b>greenrobot</b> with value <b>3.3.1</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getGreenrobot() { return getVersion("greenrobot"); }

        /**
         * Version alias <b>gson</b> with value <b>2.14.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getGson() { return getVersion("gson"); }

        /**
         * Version alias <b>guava</b> with value <b>33.6.0-android</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getGuava() { return getVersion("guava"); }

        /**
         * Version alias <b>jna</b> with value <b>5.19.1</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getJna() { return getVersion("jna"); }

        /**
         * Version alias <b>juniversalchardet</b> with value <b>1.0.3</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getJuniversalchardet() { return getVersion("juniversalchardet"); }

        /**
         * Version alias <b>jupnp</b> with value <b>3.0.4</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getJupnp() { return getVersion("jupnp"); }

        /**
         * Version alias <b>leanback</b> with value <b>1.2.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getLeanback() { return getVersion("leanback"); }

        /**
         * Version alias <b>lifecycleService</b> with value <b>2.11.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getLifecycleService() { return getVersion("lifecycleService"); }

        /**
         * Version alias <b>logger</b> with value <b>2.2.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getLogger() { return getVersion("logger"); }

        /**
         * Version alias <b>lottie</b> with value <b>6.7.1</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getLottie() { return getVersion("lottie"); }

        /**
         * Version alias <b>material</b> with value <b>1.14.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getMaterial() { return getVersion("material"); }

        /**
         * Version alias <b>materialdesigncolors</b> with value <b>1.0.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getMaterialdesigncolors() { return getVersion("materialdesigncolors"); }

        /**
         * Version alias <b>media</b> with value <b>1.8.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getMedia() { return getVersion("media"); }

        /**
         * Version alias <b>minSdk</b> with value <b>24</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getMinSdk() { return getVersion("minSdk"); }

        /**
         * Version alias <b>nanohttpd</b> with value <b>2.3.1</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getNanohttpd() { return getVersion("nanohttpd"); }

        /**
         * Version alias <b>newpipeextractor</b> with value <b>v0.26.3</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getNewpipeextractor() { return getVersion("newpipeextractor"); }

        /**
         * Version alias <b>okhttp</b> with value <b>5.4.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getOkhttp() { return getVersion("okhttp"); }

        /**
         * Version alias <b>palette</b> with value <b>1.0.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getPalette() { return getVersion("palette"); }

        /**
         * Version alias <b>permissionx</b> with value <b>1.8.1</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getPermissionx() { return getVersion("permissionx"); }

        /**
         * Version alias <b>preference</b> with value <b>1.2.1</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getPreference() { return getVersion("preference"); }

        /**
         * Version alias <b>python</b> with value <b>17.0.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getPython() { return getVersion("python"); }

        /**
         * Version alias <b>quickjs</b> with value <b>3.2.3</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getQuickjs() { return getVersion("quickjs"); }

        /**
         * Version alias <b>recyclerview</b> with value <b>1.4.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getRecyclerview() { return getVersion("recyclerview"); }

        /**
         * Version alias <b>room</b> with value <b>2.8.4</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getRoom() { return getVersion("room"); }

        /**
         * Version alias <b>rtmpClient</b> with value <b>v3.2.0.m2</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getRtmpClient() { return getVersion("rtmpClient"); }

        /**
         * Version alias <b>sardine</b> with value <b>0.9</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getSardine() { return getVersion("sardine"); }

        /**
         * Version alias <b>simpleXml</b> with value <b>2.7.1</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getSimpleXml() { return getVersion("simpleXml"); }

        /**
         * Version alias <b>smbj</b> with value <b>0.14.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getSmbj() { return getVersion("smbj"); }

        /**
         * Version alias <b>splashscreen</b> with value <b>1.2.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getSplashscreen() { return getVersion("splashscreen"); }

        /**
         * Version alias <b>startup</b> with value <b>1.2.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getStartup() { return getVersion("startup"); }

        /**
         * Version alias <b>swiperefreshlayout</b> with value <b>1.2.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getSwiperefreshlayout() { return getVersion("swiperefreshlayout"); }

        /**
         * Version alias <b>targetSdk</b> with value <b>37</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getTargetSdk() { return getVersion("targetSdk"); }

        /**
         * Version alias <b>textdrawable</b> with value <b>1.0.3</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getTextdrawable() { return getVersion("textdrawable"); }

        /**
         * Version alias <b>viewpager2</b> with value <b>1.1.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getViewpager2() { return getVersion("viewpager2"); }

        /**
         * Version alias <b>zxingAndroidEmbedded</b> with value <b>4.3.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getZxingAndroidEmbedded() { return getVersion("zxingAndroidEmbedded"); }

        /**
         * Version alias <b>zxingCore</b> with value <b>3.5.4</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getZxingCore() { return getVersion("zxingCore"); }

    }

    public static class BundleAccessors extends BundleFactory {

        public BundleAccessors(ObjectFactory objects, ProviderFactory providers, DefaultVersionCatalog config, AttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) { super(objects, providers, config, attributesFactory, capabilityNotationParser); }

        /**
         * Dependency bundle provider for <b>glide</b> which contains the following dependencies:
         * <ul>
         *    <li>com.github.bumptech.glide:glide</li>
         *    <li>com.github.bumptech.glide:annotations</li>
         *    <li>com.github.bumptech.glide:avif-integration</li>
         *    <li>com.github.bumptech.glide:okhttp3-integration</li>
         * </ul>
         * <p>
         * This bundle was declared in catalog libs.versions.toml
         */
        public Provider<ExternalModuleDependencyBundle> getGlide() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Dependency bundle provider for <b>jupnp</b> which contains the following dependencies:
         * <ul>
         *    <li>org.jupnp:org.jupnp</li>
         *    <li>org.jupnp:org.jupnp.android</li>
         *    <li>org.jupnp:org.jupnp.support</li>
         * </ul>
         * <p>
         * This bundle was declared in catalog libs.versions.toml
         */
        public Provider<ExternalModuleDependencyBundle> getJupnp() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

        /**
         * Dependency bundle provider for <b>okhttp</b> which contains the following dependencies:
         * <ul>
         *    <li>com.squareup.okhttp3:okhttp</li>
         *    <li>com.squareup.okhttp3:okhttp-dnsoverhttps</li>
         *    <li>com.squareup.okhttp3:logging-interceptor</li>
         * </ul>
         * <p>
         * This bundle was declared in catalog libs.versions.toml
         */
        public Provider<ExternalModuleDependencyBundle> getOkhttp() {
            throw new GradleException("Accessing libraries or bundles from version catalogs in the plugins block is not allowed. Only use versions or plugins from catalogs in the plugins block.");
        }

    }

    public static class PluginAccessors extends PluginFactory {
        private final AndroidPluginAccessors paccForAndroidPluginAccessors = new AndroidPluginAccessors(providers, config);
        private final ChaquoPluginAccessors paccForChaquoPluginAccessors = new ChaquoPluginAccessors(providers, config);

        public PluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Group of plugins at <b>plugins.android</b>
         */
        public AndroidPluginAccessors getAndroid() {
            return paccForAndroidPluginAccessors;
        }

        /**
         * Group of plugins at <b>plugins.chaquo</b>
         */
        public ChaquoPluginAccessors getChaquo() {
            return paccForChaquoPluginAccessors;
        }

    }

    public static class AndroidPluginAccessors extends PluginFactory {

        public AndroidPluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Plugin provider for <b>android.application</b> with plugin id <b>com.android.application</b> and
         * with version reference <b>agp</b>
         * <p>
         * This plugin was declared in catalog libs.versions.toml
         */
        public Provider<PluginDependency> getApplication() { return createPlugin("android.application"); }

        /**
         * Plugin provider for <b>android.library</b> with plugin id <b>com.android.library</b> and
         * with version reference <b>agp</b>
         * <p>
         * This plugin was declared in catalog libs.versions.toml
         */
        public Provider<PluginDependency> getLibrary() { return createPlugin("android.library"); }

    }

    public static class ChaquoPluginAccessors extends PluginFactory {

        public ChaquoPluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Plugin provider for <b>chaquo.python</b> with plugin id <b>com.chaquo.python</b> and
         * with version reference <b>python</b>
         * <p>
         * This plugin was declared in catalog libs.versions.toml
         */
        public Provider<PluginDependency> getPython() { return createPlugin("chaquo.python"); }

    }

}
