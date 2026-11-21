package cafe.kankipro.keyboard

import android.view.View
import android.view.inputmethod.EditorInfo
import cafe.kankipro.keyboard.ime.UltraKeyboardService
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** سرویس کیبورد بالا می‌آید، نمایش را می‌سازد و یک فیلد را شروع می‌کند بی خطا. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = KeyboardApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServiceTest {
    @Test fun serviceStartsAndShowsAView() {
        val ctl = Robolectric.buildService(UltraKeyboardService::class.java).create()
        val svc = ctl.get()
        val v: View = svc.onCreateInputView()
        assertNotNull(v)
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        svc.onStartInputView(EditorInfo().apply { packageName = "a.b"; inputType = 1 }, false)
        svc.onFinishInputView(true)
        ctl.destroy()
    }
}
