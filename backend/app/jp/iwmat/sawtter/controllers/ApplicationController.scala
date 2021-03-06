package jp.iwmat.sawtter.controllers

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import jp.iwmat.sawtter.services.SessionService

class ApplicationController @Inject()(
  val sessionService: SessionService
)(
  implicit
  val ec: ExecutionContext
) extends ControllerBase {

  def healthCheck = Action {
    Ok
  }
}
