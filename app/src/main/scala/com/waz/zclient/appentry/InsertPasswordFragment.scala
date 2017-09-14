/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.appentry

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.zclient.appentry.InsertPasswordFragment._
import com.waz.zclient.controllers.SignInController
import com.waz.zclient.newreg.views.PhoneConfirmationButton
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.profile.views.GuidedEditText
import com.waz.zclient.utils.{ViewUtils, _}
import com.waz.zclient.{EntryError, FragmentHelper, OnBackPressedListener, R}
import PhoneConfirmationButton.State._
import com.waz.ZLog
import com.waz.zclient.controllers.SignInController.{Login, Phone, SignInMethod}

class InsertPasswordFragment extends BaseFragment[Container] with FragmentHelper with View.OnClickListener with OnBackPressedListener {

  lazy val signInController = inject[SignInController]

  lazy val confirmationButton = Option(findById[PhoneConfirmationButton](R.id.confirmation_button))

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.insert_password_fragment, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {

    Option(findById[GuidedEditText](getView, R.id.email_field)).foreach { field =>
      field.setValidator(signInController.emailValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__email)
      signInController.email { field.setText }
      field.setEditable(false)
    }

    Option(findById[GuidedEditText](getView, R.id.password_field)).foreach { field =>
      field.setValidator(signInController.passwordValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__password)
      field.setText(signInController.password.currentValue.getOrElse(""))
      field.getEditText.addTextListener(signInController.password ! _)
    }

    confirmationButton.foreach { button =>
      button.setOnClickListener(this)
      button.setAccentColor(Color.WHITE)
    }

    setConfirmationButtonActive(signInController.isValid.currentValue.getOrElse(false))
    signInController.isValid.onUi { setConfirmationButtonActive }
    Option(findById[View](R.id.cancel_button)).foreach(_.setOnClickListener(this))
    Option(findById[View](R.id.ttv_signin_forgot_password)).foreach(_.setOnClickListener(this))
  }

  private def setConfirmationButtonActive(active: Boolean): Unit =
    confirmationButton.foreach(_.setState(if(active) CONFIRM else NONE))

  override def onClick(v: View) = {
    v.getId match {
      case R.id.cancel_button =>
        onBackPressed()
      case R.id.confirmation_button =>
        getContainer.enableProgress(true)
        signInController.attemptSignIn().map {
          case Left(error) =>
            getContainer.enableProgress(false)
            showError(error)
          case _ =>
        } (Threading.Ui)
      case R.id.ttv_signin_forgot_password =>
        getContainer.onOpenUrl(getString(R.string.url_password_reset))
      case _ =>
    }
  }

  def showError(entryError: EntryError, okCallback: => Unit = {}): Unit =
    ViewUtils.showAlertDialog(getActivity,
      entryError.headerResource,
      entryError.bodyResource,
      R.string.reg__phone_alert__button,
      new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          dialog.dismiss()
          okCallback
        }
      },
      false)

  override def onBackPressed() = {
    ZMessaging.currentAccounts.removeCurrentAccount()
    signInController.uiSignInState ! SignInMethod(Login, Phone)
    true
  }
}

object InsertPasswordFragment {
  val Tag = ZLog.ImplicitTag.implicitLogTag

  def newInstance() = new InsertPasswordFragment()
  trait Container {
    def enableProgress(enabled: Boolean): Unit
    def onOpenUrl(url: String): Unit
  }
}
