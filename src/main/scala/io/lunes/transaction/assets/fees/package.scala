package io.lunes.assets

import io.lunes.assets.fees.{Fee, NFTFee, RegularAssetFee}
import scorex.api.http.assets.IssueRequest

package object fees {

  def feeValidatorSelectionForIssueRequest(request: IssueRequest): Fee =
    if (request.reissuable == false && request.quantity == 1 && request.decimals == 0) NFTFee
    else RegularAssetFee

}