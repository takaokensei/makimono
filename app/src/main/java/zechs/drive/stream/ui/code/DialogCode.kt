package zechs.drive.stream.ui.code

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import zechs.drive.stream.R
import zechs.drive.stream.utils.util.Keyboard

class DialogCode(
    context: Context,
    val onSubmitClickListener: (String) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_code)
        window?.setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        val root = findViewById<ConstraintLayout>(R.id.root)
        val codeText = findViewById<TextInputLayout>(R.id.tf_code)
        val submitButton = findViewById<MaterialButton>(R.id.btn_submit)

        // codeText.editText should always be present given dialog_code.xml, but
        // this dialog is user-facing input, not something we want to crash on
        // if the layout ever changes underneath it.
        val editText = codeText.editText
        if (editText == null) {
            showToast(context.getString(R.string.please_enter_auth_url))
            dismiss()
            return
        }

        submitButton.setOnClickListener {
            val authCode = editText.text.toString()

            if (authCode.isEmpty()) {
                showToast(context.getString(R.string.please_enter_auth_url))
            } else {
                onSubmitClickListener.invoke(authCode)
            }

        }

        editText.requestFocus()

        editText.onFocusChangeListener =
            View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    Keyboard.show(v)
                } else {
                    Keyboard.hide(root)
                }
            }
    }

    private fun showToast(msg: String) {
        Toast.makeText(
            context, msg, Toast.LENGTH_SHORT
        ).show()
    }

}