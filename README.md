# FingerPrintAuth
Android fingerprint verification architectural reference 

本项目是繁杂业务场景结合指纹验证的**架构**参考，不是一个简单调用指纹验证API的Demo。 

# 一、背景和介绍

目前指纹领域无论从产品角度还是技术角度都已经趋于成熟，但是当各位开发者准备深入探究的时候，却发现网上很多文章和 Demo 都是皮毛，很难有较深的启示。所以本项目将展示如何在复杂的业务场景中优雅地引入指纹验证，希望能给予大家一些启发。

> 如果想要了解指纹验证开发的整个流程，包括技术选型、产品的设计方案逻辑、代码的架构以及后续测试中遇到的兼容性问题等几个方面，可以见我的博文
[如何在复杂业务场景中优雅实现Android指纹验证？](https://www.jianshu.com/p/ed880f35f97f)

# 二、项目架构


![](https://upload-images.jianshu.io/upload_images/3167794-7d0ce6d65f2dc776.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1000/format/webp)

绿色底为 Activity 层，白色底为 Util 层。

为了不和业务逻辑耦合在一起，工具类包装了一层，主要封装了验证条件的判断，指纹类的初始化等等，最主要的是封装了加密类 CryptoObjectCreatorHelper ，我们考虑到安全因素，如果不加密的话，就意味着App 无条件信任认证的结果，这个过程可能被攻击，数据可以被篡改，这是 App 在这种情况下必须承担的风险。但是这个加密过程和业务是无关的，我们不想让 Activity 层感知到，所以密钥和加密对象的销毁，会统一由工具类来把控。

为了安全，每次验证过程的密钥都不同，验证过程一结束，也就是回调 onAuthenticationSucceeded 和 onAuthenticationError 时，都需要销毁掉密钥，但是我们不想让业务层来操作，所以工具类也有自己的一个 AuthenticationCallback ，在 AuthenticationCallback 里做一些和业务无关的操作，再回调 Activity 的 AuthenticationCallbackListener 。

工具类的 CallBack 是 FingerprintManagerCompat.AuthenticationCallback 实现类，业务层的 AuthenticationCallbackListener 是自定义接口，因为不想把和业务无关的往上传递，比如说，验证成功的 AuthenticationResult ，验证错误的 typeId，这些业务并不关心。Activity 的 AuthenticationCallbackListener 会把请求统一转发给控制器 FingerPrintTypeController，在转发给控制器的前后，我们可以做一些通用的业务操作，比如说停止界面的扫描动画，发一些异步的请求等等，这个就是代理模式的应用了。

那控制器 FingerPrintTypeController 和四个场景的关系又是如何？我们看看类图：

![](https://upload-images.jianshu.io/upload_images/3167794-62681137c1d22e4b.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/641/format/webp)

可以看到，不同的场景对应不同的状态类，控制器和状态类实现了同一个接口，在内部根据当前场景转发给对应的类。
