package com.nteligen.lemminx.dfdl.models.settings;

import com.google.gson.annotations.JsonAdapter;
import org.eclipse.lsp4j.jsonrpc.json.adapters.JsonElementTypeAdapter;

/**
 * Model for top level xml settings JSON object.
 */
public class AllSettings {
  @JsonAdapter(JsonElementTypeAdapter.Factory.class)
  private Object Dfdl;

  public Object getDfdl() {
    return Dfdl;
  }

  public void setDfdl(Object Dfdl) {
    this.Dfdl = Dfdl;
  }

}
