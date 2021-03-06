/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.xxl.bdmap.ui.act;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.baidu.location.BDLocation;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.route.BikingRouteResult;
import com.baidu.mapapi.search.route.DrivingRouteResult;
import com.baidu.mapapi.search.route.IndoorRouteResult;
import com.baidu.mapapi.search.route.MassTransitRouteResult;
import com.baidu.mapapi.search.route.OnGetRoutePlanResultListener;
import com.baidu.mapapi.search.route.PlanNode;
import com.baidu.mapapi.search.route.RoutePlanSearch;
import com.baidu.mapapi.search.route.TransitRouteResult;
import com.baidu.mapapi.search.route.WalkingRoutePlanOption;
import com.baidu.mapapi.search.route.WalkingRouteResult;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.lxj.xpopup.XPopup;
import com.xxl.bdmap.R;
import com.xxl.bdmap.data.AddressResult;
import com.xxl.bdmap.data.IfLyMapData;
import com.xxl.bdmap.ui.LocationListBottomPop;
import com.xxl.bdmap.ui.map.BDLocationUtil;
import com.xxl.bdmap.ui.map.RouteLineAdapter;
import com.xxl.bdmap.ui.map.SelectRouteDialog;
import com.xxl.bdmap.ui.map.WalkingRouteOverlay;
import com.xxl.bdmap.utils.MapConvertUtil;

import java.util.ArrayList;
import java.util.HashMap;


/**
 * Title: WalkingRouteSearchDemos.java
 * Description: ??????????????????
 * Copyright (c) ??????????????????????????? 2021
 * Created DateTime: 2022/2/14 16:15
 * Created by xueli.
 */
public class WalkingRouteSearchActivity extends AppCompatActivity implements BaiduMap.OnMapClickListener,
        OnGetRoutePlanResultListener {

    // ????????????????????????

    // ???????????????????????????MapView???MyRouteMapView???????????????touch????????????????????????
    // ???????????????touch???????????????????????????????????????MapView??????
    private MapView mMapView = null;    // ??????View
    private BaiduMap mBaiduMap = null;
    // ???????????????????????????????????????????????????
    private RoutePlanSearch mSearch = null;
    private WalkingRouteResult mWalkingRouteResult = null;
    private boolean hasShowDialog = false;
    private IfLyMapData mapData;
    ArrayList<AddressResult> locationList;
    private MyLocationData myLocationData;


    //?????????????????????
    private LatLng mCurrentLatlng;

    LocationListBottomPop pop;
    private boolean isFirstLocate = true;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walking_route);


        // ???????????????
        mMapView = (MapView) findViewById(R.id.map);
        mBaiduMap = mMapView.getMap();
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (pop != null && pop.isShow()) {
                    pop.dismiss();
                }
                finish();
            }
        });

        getBundleData();
        initMap();


    }

    private void initMap() {
        BDLocationUtil.getInstance().startLocation(this, new BDLocationUtil.LocationListener() {
            @Override
            public void onLocation(BDLocation location) {
                Log.d("xxl", location.getCity() + "latu:" + location.getLatitude());
                mCurrentLatlng = new LatLng(location.getLatitude(), location.getLongitude());

                if (isFirstLocate) {
                    isFirstLocate = false;
                    //?????????????????????
                    // ???????????????????????????????????????
                    mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(mCurrentLatlng, 19f));
                }
                MyLocationData locData = new MyLocationData.Builder()
                        .accuracy(location.getRadius())
                        // ?????????????????????????????????????????????????????????0-360
                        .direction(location.getDirection()).latitude(location.getLatitude())
                        .longitude(location.getLongitude()).build();
                mBaiduMap.setMyLocationData(locData);
            }

            @Override
            public void onLocationFailed() {
                mCurrentLatlng = new LatLng(
                        30.19319,
                        120.191436);
            }
        });
        //???????????????????????????
        mBaiduMap.setMyLocationEnabled(true);
        // ????????????????????????
        mBaiduMap.setOnMapClickListener(this);

        // ??????????????????????????????????????????
        mSearch = RoutePlanSearch.newInstance();
        mSearch.setOnGetRoutePlanResultListener(this);


    }

    private void getBundleData() {
        Intent intent = getIntent();
        Bundle bundle = getIntent().getExtras();
        if (intent != null && bundle != null) {
            mapData = (IfLyMapData) bundle.getSerializable("iflymapdata");
            if (mapData != null && mapData.getData() != null) {
                locationList = (ArrayList<AddressResult>) mapData.getData().getResult();
                if (locationList.size() <= 0) return;
                // ??????????????????
                pop = new LocationListBottomPop(this, locationList);
                if (!pop.isShow()) {
//                    pop.show();
                    new XPopup.Builder(WalkingRouteSearchActivity.this)
                            .moveUpToKeyboard(false) //????????????????????????????????????????????????????????????
                            .dismissOnTouchOutside(false)
                            .enableDrag(true)
                            .isThreeDrag(true)
                            .asCustom(pop/*.enableDrag(false)*/)
                            .show();
                }
                pop.setItemClickListener(new OnItemClickListener() {
                    @Override
                    public void onItemClick(@NonNull BaseQuickAdapter<?, ?> adapter, @NonNull View view, int position) {
                        showRoad(locationList.get(position));
                        pop.dismiss();
                    }
                });

                showRoad(locationList.get(0));
            }


        }
    }

    public void showRoad(AddressResult result) {
        mMapView.postDelayed(new Runnable() {
            @Override
            public void run() {
                searchButtonProcess(result);
            }
        }, 1 * 1000);
    }

    /**
     * ??????????????????????????????
     */
    public void searchButtonProcess(AddressResult addr) {
        if (addr == null)
            return;
        // ????????????????????????
        mBaiduMap.clear();
        // ????????????????????? ????????????
//        PlanNode startNode = PlanNode.withCityNameAndPlaceName("??????",
//                "???????????????");
        PlanNode startNode = PlanNode.withLocation(mCurrentLatlng);
        // ????????????
        PlanNode endNode;
        if (!TextUtils.isEmpty(addr.getLatitude())) {//?????????????????????????????????????????????
            HashMap map = MapConvertUtil.bd_encrypt(Double.parseDouble(addr.getLatitude()), Double.parseDouble(addr.getLongitude()));
            LatLng endLoc = new LatLng((Double) map.get("bd_lat"), (Double) map.get("bd_lon"));
            endNode = PlanNode.withLocation(endLoc);
        } else {
            endNode = PlanNode.withCityNameAndPlaceName("??????", "????????????");
        }
//
        // ????????????????????????????????????????????????????????????
        mSearch.walkingSearch((new WalkingRoutePlanOption())
                .from(startNode) // ??????
                .to(endNode)); // ??????

    }


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    /**
     * ????????????????????????
     *
     * @param result ??????????????????
     */
    @Override
    public void onGetWalkingRouteResult(WalkingRouteResult result) {
        if (null == result) {
            return;
        }
        if (result.error == SearchResult.ERRORNO.AMBIGUOUS_ROURE_ADDR) {
            Log.d("xxl", result.getSuggestAddrInfo().getSuggestStartNode().size() + "");
            // ?????????????????????????????????????????????????????????????????????????????????
            result.getSuggestAddrInfo();
            AlertDialog.Builder builder = new AlertDialog.Builder(WalkingRouteSearchActivity.this);
            builder.setTitle("??????");
            builder.setMessage("??????????????????????????????????????????\n?????????getSuggestAddrInfo()??????????????????????????????");
            builder.setPositiveButton("??????", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.create().show();
            return;
        }

        if (result.error != SearchResult.ERRORNO.NO_ERROR) {
            Toast.makeText(WalkingRouteSearchActivity.this, "????????????????????????", Toast.LENGTH_SHORT).show();
        } else {

            if (result.getRouteLines().size() > 1) {
                mWalkingRouteResult = result;
                if (!hasShowDialog) {
                    SelectRouteDialog selectRouteDialog = new SelectRouteDialog(WalkingRouteSearchActivity.this,
                            result.getRouteLines(), RouteLineAdapter.Type.WALKING_ROUTE);
                    selectRouteDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            hasShowDialog = false;
                        }
                    });

                    selectRouteDialog.show();
                    hasShowDialog = true;
                }
            } else if (result.getRouteLines().size() == 1) {
                // ????????????
//                mRouteLine = result.getRouteLines().get(0);
                WalkingRouteOverlay overlay = new MyWalkingRouteOverlay(mBaiduMap);
                mBaiduMap.setOnMarkerClickListener(overlay);
//                mRouteOverlay = overlay;
                overlay.setData(result.getRouteLines().get(0));
                overlay.addToMap();
                overlay.zoomToSpan();

            } else {
                Log.d("route result", "?????????<0");
            }
        }
    }

    @Override
    public void onGetTransitRouteResult(TransitRouteResult result) {
    }

    @Override
    public void onGetMassTransitRouteResult(MassTransitRouteResult result) {
    }

    @Override
    public void onGetDrivingRouteResult(DrivingRouteResult result) {
    }

    @Override
    public void onGetIndoorRouteResult(IndoorRouteResult indoorRouteResult) {

    }

    @Override
    public void onGetBikingRouteResult(BikingRouteResult result) {

    }

    private class MyWalkingRouteOverlay extends WalkingRouteOverlay {

        private MyWalkingRouteOverlay(BaiduMap baiduMap) {
            super(baiduMap);
        }

        @Override
        public BitmapDescriptor getStartMarker() {
            if (true) {
                return BitmapDescriptorFactory.fromResource(R.drawable.icon_st);
            }
            return null;
        }

        @Override
        public BitmapDescriptor getTerminalMarker() {
            if (true) {
                return BitmapDescriptorFactory.fromResource(R.drawable.icon_en);
            }
            return null;
        }

    }

    @Override
    public void onMapClick(LatLng point) {
        mBaiduMap.hideInfoWindow();
    }

    @Override
    public void onMapPoiClick(MapPoi poi) {

    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ??????????????????
        if (mSearch != null) {
            mSearch.destroy();
        }
        BDLocationUtil.getInstance().stop();
        // ??????????????????
        mBaiduMap.setMyLocationEnabled(false);
        mBaiduMap.clear();
        mMapView.onDestroy();
    }

    public MyLocationData getMyLocationData() {
        if (null == myLocationData) {
            //120.169558,30.258713?????????????????????????????????
            //120.169553,30.258841
            myLocationData = new MyLocationData.Builder()
                    //?????????????????????????????????0.0f
                    .accuracy(0)
                    // ?????????????????????????????????????????????????????????0-360
                    .direction(270)
                    //??????????????????30.25965, 120.167734
                    .latitude(mCurrentLatlng.latitude)
                    //??????????????????
                    .longitude(mCurrentLatlng.longitude)
                    .build();
        }
        return myLocationData;
    }
}
