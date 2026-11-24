/* 옛 셀 저장소 (table_name, row_key) ↔ 규격 표 (alias, field) 매핑 — 생성 파일 (energy_specs.py → gen_energy_all.py). 손으로 고치지 말 것.
 * 자바 쪽 EnergyCellMapData 와 같은 스펙에서 나온다. 열 규약(colRule) 해석은 energy-store.js 에 있다.
 */
window.EnergyCellMap = {
 "tables": {
  "accountingPlan": {
   "path": "/energy-accounting-plans",
   "grain": "month",
   "colRule": "eap",
   "fields": {
    "compositeSteamBuy": "input",
    "zescoSteamBuy": "input",
    "lngBuy": "input",
    "incineratorCost": "input",
    "fluidCost": "input",
    "zescoOffset": "input",
    "zescoPenalty": "input",
    "zescoAdvanceRepay": "input",
    "compositeLngWater": "input",
    "compositeInvest": "input",
    "compositeIncome": "input",
    "landLease": "input",
    "paperFuel": "calc",
    "livingFuel": "calc",
    "fuelVsPlan": "input",
    "paperProdTon": "input",
    "tissueProdTon": "input",
    "steamProdTon": "input",
    "lngBurnerCost": "input",
    "compositeNet": "calc",
    "zescoNet": "calc"
   }
  },
  "fuelPlan": {
   "path": "/energy-fuel-plans",
   "grain": "month",
   "colRule": "efp",
   "fields": {
    "paperProdTon": "input",
    "tissueProdTon": "input",
    "paperUnit": "input",
    "tissueUnit": "input",
    "paperTotalSteam": "calc",
    "tissueTotalSteam": "calc",
    "etcDisperser": "input",
    "etcToc": "input",
    "operatingDays": "input",
    "prodIncinerator": "input",
    "prodComposite": "input",
    "prodExtBoiler": "input",
    "prodFluid": "input",
    "lngComposite": "input",
    "lngTm3": "input",
    "lngTm4": "input",
    "lngTm5": "input",
    "lngPm3": "input",
    "priceLng": "input",
    "priceIncinerator": "input",
    "priceComposite": "input",
    "priceExtBoiler": "input",
    "priceFluid": "input",
    "opexIncinerator": "input",
    "opexFluid": "input",
    "payExtAdvance": "input",
    "payExtPenalty": "input",
    "payIncinerator": "input",
    "payFluid": "input"
   }
  },
  "ratioBoiler": {
   "path": "/energy-ratio-boilers",
   "grain": "month",
   "colRule": "efr",
   "fields": {
    "steamLngBoiler": "input",
    "steamComposite": "input",
    "steamIncinerator": "input",
    "steamFluid": "input",
    "steamExtBoiler": "input",
    "fluidVentTon": "input",
    "compositePenaltyTon": "input",
    "compositeGuaranteeTon": "input",
    "costLngBoiler": "input",
    "costComposite": "calc",
    "costIncinerator": "calc",
    "costFluid": "calc",
    "costExtBoiler": "calc",
    "fluidInternalVentCost": "input",
    "priceLngBoiler": "calc",
    "priceComposite": "calc",
    "priceIncinerator": "calc",
    "priceFluid": "calc",
    "priceExtBoiler": "calc"
   }
  },
  "ratioMachine": {
   "path": "/energy-ratio-machines",
   "grain": "month",
   "colRule": "efr",
   "fields": {
    "fuelPm2": "input",
    "fuelPm3": "input",
    "fuelPaperSubtotal": "calc",
    "fuelTm3": "input",
    "fuelTm4": "input",
    "fuelTm5": "input",
    "fuelTissueSubtotal": "calc",
    "fuelSteamProdTon": "input",
    "usagePm2": "input",
    "usagePm3": "input",
    "usageTm3": "input",
    "usageTm4": "input",
    "usageTm5": "input",
    "usageTotalProdSteam": "input",
    "usageEtc": "calc",
    "prodPm2Kg": "input",
    "prodPm3Kg": "input",
    "prodPaperSubtotalKg": "calc",
    "prodTm3Kg": "input",
    "prodTm4Kg": "input",
    "prodTm5Kg": "input",
    "prodTissueSubtotalKg": "calc"
   }
  },
  "ratioBurner": {
   "path": "/energy-ratio-burners",
   "grain": "month",
   "colRule": "efr",
   "fields": {
    "lngPm3": "input",
    "lngTm3": "input",
    "lngTm4": "input",
    "lngTm5": "input",
    "lngPricePrevYear": "input",
    "lngPrice": "input",
    "lngPriceChangeRate": "calc",
    "lngCostTotal": "calc",
    "lngUnitPm3": "calc",
    "lngUnitTm3": "calc",
    "lngUnitTm4": "calc",
    "lngUnitTm5": "calc",
    "lngUnitTissueSubtotal": "calc"
   }
  },
  "ratioUnit": {
   "path": "/energy-ratio-steam-units",
   "grain": "month",
   "colRule": "efr",
   "fields": {
    "unitPm2": "calc",
    "unitPm3": "calc",
    "unitPaperSubtotal": "calc",
    "unitTm3": "calc",
    "unitTm4": "calc",
    "unitTm5": "calc",
    "unitTissueSubtotal": "calc"
   }
  },
  "ratioRef2019": {
   "path": "/energy-ratio-ref2019s",
   "grain": "month",
   "colRule": "efr",
   "fields": {
    "t18Pm1": "input",
    "t18Pm2": "input",
    "t18Pm3": "input",
    "t18PaperSubtotal": "input",
    "t18Tm1": "input",
    "t18Tm3": "input",
    "t18Tm4": "input",
    "t18Tm5": "input",
    "t18TissueSubtotal": "input",
    "t18Total": "input",
    "t19Pm1": "input",
    "t19Pm2": "input",
    "t19Pm3": "input",
    "t19PaperSubtotal": "input",
    "t19Tm1": "input",
    "t19Tm3": "input",
    "t19Tm4": "input",
    "t19Tm5": "input",
    "t19TissueSubtotal": "input",
    "t19Total": "input",
    "t20Pm1": "input",
    "t20Pm2": "input",
    "t20Pm3": "input",
    "t20PaperSubtotal": "input",
    "t20Tm1": "input",
    "t20Tm3": "input",
    "t20Tm4": "input",
    "t20Tm5": "input",
    "t20TissueSubtotal": "input",
    "t20Total": "input",
    "t20TotalEnergyCost": "input"
   }
  },
  "monthCompare": {
   "path": "/energy-month-compares",
   "grain": "month",
   "colRule": "emc",
   "fields": {
    "zescoSteamBuy": "input",
    "lngBuy": "input",
    "zescoOffset": "input",
    "zescoPenalty": "input",
    "fuelPlan": "input",
    "incineratorSteamBuy": "calc",
    "fluidSteamBuy": "calc",
    "fuelActual": "calc",
    "fuelSteamUnit": "calc"
   }
  },
  "monthCompareNote": {
   "path": "/energy-month-compare-notes",
   "grain": "year",
   "colRule": "emcNote",
   "fields": {
    "noteFuel": "text",
    "noteDeduct": "text",
    "noteActual": "text",
    "noteSteamRatio": "text",
    "notePlan": "text",
    "noteProd": "text",
    "noteProduct": "text",
    "noteUnit": "text",
    "noteProfit": "text",
    "noteAux": "text"
   }
  },
  "fuelSummary": {
   "path": "/energy-fuel-summaries",
   "grain": "month",
   "colRule": "efs",
   "fields": {
    "t1Pm2Prod": "input",
    "t1Pm2Fuel": "input",
    "t1Pm3Prod": "input",
    "t1Pm3Fuel": "input",
    "t1Tm3Prod": "input",
    "t1Tm3Fuel": "input",
    "t1Tm4Prod": "input",
    "t1Tm4Fuel": "input",
    "t1Tm5Prod": "input",
    "t1Tm5Fuel": "input",
    "t2LngUsage": "input",
    "t2LngPrice": "input",
    "t2LngCost": "input",
    "t2CompositeUsage": "input",
    "t2CompositePrice": "input",
    "t2CompositeCost": "input",
    "t2IncineratorUsage": "input",
    "t2IncineratorPrice": "input",
    "t2IncineratorCost": "input",
    "t2FluidUsage": "input",
    "t2FluidPrice": "input",
    "t2FluidCost": "input",
    "t2ExtUsage": "input",
    "t2ExtPrice": "input",
    "t2ExtCost": "input",
    "t2LngElec": "input",
    "t2LngPenalty": "input",
    "t2LngOverprice": "input",
    "t2CompositeElec": "input",
    "t2CompositePenalty": "input",
    "t2CompositeOverprice": "input",
    "t2IncineratorElec": "input",
    "t2IncineratorPenalty": "input",
    "t2IncineratorOverprice": "input",
    "t2FluidElec": "input",
    "t2FluidPenalty": "input",
    "t2FluidOverprice": "input",
    "t2ExtElec": "input",
    "t2ExtPenalty": "input",
    "t2ExtOverprice": "input",
    "t2SubtotalElec": "input",
    "t2SubtotalPenalty": "input",
    "t2SubtotalOverprice": "input",
    "t2BurnerLngElec": "input",
    "t2BurnerLngPenalty": "input",
    "t2BurnerLngOverprice": "input",
    "t2TotalElec": "input",
    "t2TotalPenalty": "input",
    "t2TotalOverprice": "input"
   }
  }
 },
 "legacy": {
  "energy_accounting_plan": {
   "r01": [
    "accountingPlan",
    "compositeSteamBuy"
   ],
   "r02": [
    "accountingPlan",
    "zescoSteamBuy"
   ],
   "r03": [
    "accountingPlan",
    "lngBuy"
   ],
   "r04": [
    "accountingPlan",
    "incineratorCost"
   ],
   "r05": [
    "accountingPlan",
    "fluidCost"
   ],
   "r07": [
    "accountingPlan",
    "zescoOffset"
   ],
   "r08": [
    "accountingPlan",
    "zescoPenalty"
   ],
   "r09": [
    "accountingPlan",
    "zescoAdvanceRepay"
   ],
   "r10": [
    "accountingPlan",
    "compositeLngWater"
   ],
   "r11": [
    "accountingPlan",
    "compositeInvest"
   ],
   "r12": [
    "accountingPlan",
    "compositeIncome"
   ],
   "r13": [
    "accountingPlan",
    "landLease"
   ],
   "r16": [
    "accountingPlan",
    "paperFuel"
   ],
   "r17": [
    "accountingPlan",
    "livingFuel"
   ],
   "r19": [
    "accountingPlan",
    "fuelVsPlan"
   ],
   "r22": [
    "accountingPlan",
    "paperProdTon"
   ],
   "r23": [
    "accountingPlan",
    "tissueProdTon"
   ],
   "r31": [
    "accountingPlan",
    "steamProdTon"
   ],
   "r32": [
    "accountingPlan",
    "lngBurnerCost"
   ],
   "r36": [
    "accountingPlan",
    "compositeNet"
   ],
   "r37": [
    "accountingPlan",
    "zescoNet"
   ]
  },
  "energy_fuel_plan": {
   "t01|r01": [
    "fuelPlan",
    "paperProdTon"
   ],
   "t01|r02": [
    "fuelPlan",
    "tissueProdTon"
   ],
   "t01|r04": [
    "fuelPlan",
    "paperUnit"
   ],
   "t01|r05": [
    "fuelPlan",
    "tissueUnit"
   ],
   "t01|r11": [
    "fuelPlan",
    "paperTotalSteam"
   ],
   "t01|r12": [
    "fuelPlan",
    "tissueTotalSteam"
   ],
   "t02|r01": [
    "fuelPlan",
    "etcDisperser"
   ],
   "t02|r04": [
    "fuelPlan",
    "etcToc"
   ],
   "t03|r02": [
    "fuelPlan",
    "operatingDays"
   ],
   "t04|r02": [
    "fuelPlan",
    "prodIncinerator"
   ],
   "t04|r03": [
    "fuelPlan",
    "prodComposite"
   ],
   "t04|r04": [
    "fuelPlan",
    "prodExtBoiler"
   ],
   "t04|r05": [
    "fuelPlan",
    "prodFluid"
   ],
   "t06|r02": [
    "fuelPlan",
    "lngComposite"
   ],
   "t06|r03": [
    "fuelPlan",
    "lngTm3"
   ],
   "t06|r04": [
    "fuelPlan",
    "lngTm4"
   ],
   "t06|r05": [
    "fuelPlan",
    "lngTm5"
   ],
   "t06|r07": [
    "fuelPlan",
    "lngPm3"
   ],
   "t07|r01": [
    "fuelPlan",
    "priceLng"
   ],
   "t07|r02": [
    "fuelPlan",
    "priceIncinerator"
   ],
   "t07|r03": [
    "fuelPlan",
    "priceComposite"
   ],
   "t07|r04": [
    "fuelPlan",
    "priceExtBoiler"
   ],
   "t07|r05": [
    "fuelPlan",
    "priceFluid"
   ],
   "t08|r02": [
    "fuelPlan",
    "opexIncinerator"
   ],
   "t08|r05": [
    "fuelPlan",
    "opexFluid"
   ],
   "t09|r04": [
    "fuelPlan",
    "payExtAdvance"
   ],
   "t09|r05": [
    "fuelPlan",
    "payExtPenalty"
   ],
   "t09|r06": [
    "fuelPlan",
    "payIncinerator"
   ],
   "t09|r07": [
    "fuelPlan",
    "payFluid"
   ]
  },
  "energy_fuel_ratio": {
   "t01|r01": [
    "ratioBoiler",
    "steamLngBoiler"
   ],
   "t01|r02": [
    "ratioBoiler",
    "steamComposite"
   ],
   "t01|r03": [
    "ratioBoiler",
    "steamIncinerator"
   ],
   "t01|r04": [
    "ratioBoiler",
    "steamFluid"
   ],
   "t01|r05": [
    "ratioBoiler",
    "steamExtBoiler"
   ],
   "t01|r07": [
    "ratioBoiler",
    "fluidVentTon"
   ],
   "t01|r08": [
    "ratioBoiler",
    "compositePenaltyTon"
   ],
   "t01|r09": [
    "ratioBoiler",
    "compositeGuaranteeTon"
   ],
   "t02|r01": [
    "ratioBoiler",
    "costLngBoiler"
   ],
   "t02|r02": [
    "ratioBoiler",
    "costComposite"
   ],
   "t02|r03": [
    "ratioBoiler",
    "costIncinerator"
   ],
   "t02|r04": [
    "ratioBoiler",
    "costFluid"
   ],
   "t02|r05": [
    "ratioBoiler",
    "costExtBoiler"
   ],
   "t02|r07": [
    "ratioBoiler",
    "fluidInternalVentCost"
   ],
   "t03|r01": [
    "ratioBoiler",
    "priceLngBoiler"
   ],
   "t03|r02": [
    "ratioBoiler",
    "priceComposite"
   ],
   "t03|r03": [
    "ratioBoiler",
    "priceIncinerator"
   ],
   "t03|r04": [
    "ratioBoiler",
    "priceFluid"
   ],
   "t03|r05": [
    "ratioBoiler",
    "priceExtBoiler"
   ],
   "t04|r01": [
    "ratioMachine",
    "fuelPm2"
   ],
   "t04|r02": [
    "ratioMachine",
    "fuelPm3"
   ],
   "t04|r03": [
    "ratioMachine",
    "fuelPaperSubtotal"
   ],
   "t04|r04": [
    "ratioMachine",
    "fuelTm3"
   ],
   "t04|r05": [
    "ratioMachine",
    "fuelTm4"
   ],
   "t04|r06": [
    "ratioMachine",
    "fuelTm5"
   ],
   "t04|r07": [
    "ratioMachine",
    "fuelTissueSubtotal"
   ],
   "t04|r10": [
    "ratioMachine",
    "fuelSteamProdTon"
   ],
   "t05|r01": [
    "ratioMachine",
    "usagePm2"
   ],
   "t05|r02": [
    "ratioMachine",
    "usagePm3"
   ],
   "t05|r04": [
    "ratioMachine",
    "usageTm3"
   ],
   "t05|r05": [
    "ratioMachine",
    "usageTm4"
   ],
   "t05|r06": [
    "ratioMachine",
    "usageTm5"
   ],
   "t05|r09": [
    "ratioMachine",
    "usageTotalProdSteam"
   ],
   "t05|r10": [
    "ratioMachine",
    "usageEtc"
   ],
   "t06|r01": [
    "ratioMachine",
    "prodPm2Kg"
   ],
   "t06|r02": [
    "ratioMachine",
    "prodPm3Kg"
   ],
   "t06|r03": [
    "ratioMachine",
    "prodPaperSubtotalKg"
   ],
   "t06|r04": [
    "ratioMachine",
    "prodTm3Kg"
   ],
   "t06|r05": [
    "ratioMachine",
    "prodTm4Kg"
   ],
   "t06|r06": [
    "ratioMachine",
    "prodTm5Kg"
   ],
   "t06|r07": [
    "ratioMachine",
    "prodTissueSubtotalKg"
   ],
   "t10|r01": [
    "ratioBurner",
    "lngPm3"
   ],
   "t10|r02": [
    "ratioBurner",
    "lngTm3"
   ],
   "t10|r03": [
    "ratioBurner",
    "lngTm4"
   ],
   "t10|r04": [
    "ratioBurner",
    "lngTm5"
   ],
   "t11|r01": [
    "ratioBurner",
    "lngPricePrevYear"
   ],
   "t11|r02": [
    "ratioBurner",
    "lngPrice"
   ],
   "t11|r03": [
    "ratioBurner",
    "lngPriceChangeRate"
   ],
   "t11|r09": [
    "ratioBurner",
    "lngCostTotal"
   ],
   "t12|r01": [
    "ratioBurner",
    "lngUnitPm3"
   ],
   "t12|r02": [
    "ratioBurner",
    "lngUnitTm3"
   ],
   "t12|r03": [
    "ratioBurner",
    "lngUnitTm4"
   ],
   "t12|r04": [
    "ratioBurner",
    "lngUnitTm5"
   ],
   "t12|r05": [
    "ratioBurner",
    "lngUnitTissueSubtotal"
   ],
   "t17|r01": [
    "ratioUnit",
    "unitPm2"
   ],
   "t17|r02": [
    "ratioUnit",
    "unitPm3"
   ],
   "t17|r03": [
    "ratioUnit",
    "unitPaperSubtotal"
   ],
   "t17|r04": [
    "ratioUnit",
    "unitTm3"
   ],
   "t17|r05": [
    "ratioUnit",
    "unitTm4"
   ],
   "t17|r06": [
    "ratioUnit",
    "unitTm5"
   ],
   "t17|r07": [
    "ratioUnit",
    "unitTissueSubtotal"
   ],
   "t18|r01": [
    "ratioRef2019",
    "t18Pm1"
   ],
   "t18|r02": [
    "ratioRef2019",
    "t18Pm2"
   ],
   "t18|r03": [
    "ratioRef2019",
    "t18Pm3"
   ],
   "t18|r04": [
    "ratioRef2019",
    "t18PaperSubtotal"
   ],
   "t18|r05": [
    "ratioRef2019",
    "t18Tm1"
   ],
   "t18|r06": [
    "ratioRef2019",
    "t18Tm3"
   ],
   "t18|r07": [
    "ratioRef2019",
    "t18Tm4"
   ],
   "t18|r08": [
    "ratioRef2019",
    "t18Tm5"
   ],
   "t18|r09": [
    "ratioRef2019",
    "t18TissueSubtotal"
   ],
   "t18|r10": [
    "ratioRef2019",
    "t18Total"
   ],
   "t19|r01": [
    "ratioRef2019",
    "t19Pm1"
   ],
   "t19|r02": [
    "ratioRef2019",
    "t19Pm2"
   ],
   "t19|r03": [
    "ratioRef2019",
    "t19Pm3"
   ],
   "t19|r04": [
    "ratioRef2019",
    "t19PaperSubtotal"
   ],
   "t19|r05": [
    "ratioRef2019",
    "t19Tm1"
   ],
   "t19|r06": [
    "ratioRef2019",
    "t19Tm3"
   ],
   "t19|r07": [
    "ratioRef2019",
    "t19Tm4"
   ],
   "t19|r08": [
    "ratioRef2019",
    "t19Tm5"
   ],
   "t19|r09": [
    "ratioRef2019",
    "t19TissueSubtotal"
   ],
   "t19|r10": [
    "ratioRef2019",
    "t19Total"
   ],
   "t20|r01": [
    "ratioRef2019",
    "t20Pm1"
   ],
   "t20|r02": [
    "ratioRef2019",
    "t20Pm2"
   ],
   "t20|r03": [
    "ratioRef2019",
    "t20Pm3"
   ],
   "t20|r04": [
    "ratioRef2019",
    "t20PaperSubtotal"
   ],
   "t20|r05": [
    "ratioRef2019",
    "t20Tm1"
   ],
   "t20|r06": [
    "ratioRef2019",
    "t20Tm3"
   ],
   "t20|r07": [
    "ratioRef2019",
    "t20Tm4"
   ],
   "t20|r08": [
    "ratioRef2019",
    "t20Tm5"
   ],
   "t20|r09": [
    "ratioRef2019",
    "t20TissueSubtotal"
   ],
   "t20|r10": [
    "ratioRef2019",
    "t20Total"
   ],
   "t20|r11": [
    "ratioRef2019",
    "t20TotalEnergyCost"
   ]
  },
  "energy_month_compare": {
   "fuel|zescoSteam": [
    "monthCompare",
    "zescoSteamBuy"
   ],
   "fuel|lngBuy": [
    "monthCompare",
    "lngBuy"
   ],
   "fuel|zescoOffset": [
    "monthCompare",
    "zescoOffset"
   ],
   "fuel|zescoPenalty": [
    "monthCompare",
    "zescoPenalty"
   ],
   "fuel|fuelPlan": [
    "monthCompare",
    "fuelPlan"
   ],
   "fuel|pyhsynSteam": [
    "monthCompare",
    "incineratorSteamBuy"
   ],
   "fuel|fluidSteam": [
    "monthCompare",
    "fluidSteamBuy"
   ],
   "fuel|fuelActual": [
    "monthCompare",
    "fuelActual"
   ],
   "fuel|fuelSteamUnit": [
    "monthCompare",
    "fuelSteamUnit"
   ],
   "note|fuel|연료비": [
    "monthCompareNote",
    "noteFuel"
   ],
   "note|fuel|차감비": [
    "monthCompareNote",
    "noteDeduct"
   ],
   "note|fuel|실적": [
    "monthCompareNote",
    "noteActual"
   ],
   "note|fuel|스팀사용비율": [
    "monthCompareNote",
    "noteSteamRatio"
   ],
   "note|fuel|계획": [
    "monthCompareNote",
    "notePlan"
   ],
   "note|fuel|생산량": [
    "monthCompareNote",
    "noteProd"
   ],
   "note|fuel|제품생산량": [
    "monthCompareNote",
    "noteProduct"
   ],
   "note|fuel|원단위": [
    "monthCompareNote",
    "noteUnit"
   ],
   "note|fuel|손익": [
    "monthCompareNote",
    "noteProfit"
   ],
   "note|fuel|보조 분석": [
    "monthCompareNote",
    "noteAux"
   ]
  },
  "energy_fuel_summary": {
   "t1|0|prod": [
    "fuelSummary",
    "t1Pm2Prod"
   ],
   "t1|0|fuel": [
    "fuelSummary",
    "t1Pm2Fuel"
   ],
   "t1|1|prod": [
    "fuelSummary",
    "t1Pm3Prod"
   ],
   "t1|1|fuel": [
    "fuelSummary",
    "t1Pm3Fuel"
   ],
   "t1|3|prod": [
    "fuelSummary",
    "t1Tm3Prod"
   ],
   "t1|3|fuel": [
    "fuelSummary",
    "t1Tm3Fuel"
   ],
   "t1|4|prod": [
    "fuelSummary",
    "t1Tm4Prod"
   ],
   "t1|4|fuel": [
    "fuelSummary",
    "t1Tm4Fuel"
   ],
   "t1|5|prod": [
    "fuelSummary",
    "t1Tm5Prod"
   ],
   "t1|5|fuel": [
    "fuelSummary",
    "t1Tm5Fuel"
   ],
   "t2|0|usage": [
    "fuelSummary",
    "t2LngUsage"
   ],
   "t2|0|price": [
    "fuelSummary",
    "t2LngPrice"
   ],
   "t2|0|cost": [
    "fuelSummary",
    "t2LngCost"
   ],
   "t2|1|usage": [
    "fuelSummary",
    "t2CompositeUsage"
   ],
   "t2|1|price": [
    "fuelSummary",
    "t2CompositePrice"
   ],
   "t2|1|cost": [
    "fuelSummary",
    "t2CompositeCost"
   ],
   "t2|2|usage": [
    "fuelSummary",
    "t2IncineratorUsage"
   ],
   "t2|2|price": [
    "fuelSummary",
    "t2IncineratorPrice"
   ],
   "t2|2|cost": [
    "fuelSummary",
    "t2IncineratorCost"
   ],
   "t2|3|usage": [
    "fuelSummary",
    "t2FluidUsage"
   ],
   "t2|3|price": [
    "fuelSummary",
    "t2FluidPrice"
   ],
   "t2|3|cost": [
    "fuelSummary",
    "t2FluidCost"
   ],
   "t2|4|usage": [
    "fuelSummary",
    "t2ExtUsage"
   ],
   "t2|4|price": [
    "fuelSummary",
    "t2ExtPrice"
   ],
   "t2|4|cost": [
    "fuelSummary",
    "t2ExtCost"
   ],
   "t2|0|elec": [
    "fuelSummary",
    "t2LngElec"
   ],
   "t2|0|penalty": [
    "fuelSummary",
    "t2LngPenalty"
   ],
   "t2|0|overprice": [
    "fuelSummary",
    "t2LngOverprice"
   ],
   "t2|1|elec": [
    "fuelSummary",
    "t2CompositeElec"
   ],
   "t2|1|penalty": [
    "fuelSummary",
    "t2CompositePenalty"
   ],
   "t2|1|overprice": [
    "fuelSummary",
    "t2CompositeOverprice"
   ],
   "t2|2|elec": [
    "fuelSummary",
    "t2IncineratorElec"
   ],
   "t2|2|penalty": [
    "fuelSummary",
    "t2IncineratorPenalty"
   ],
   "t2|2|overprice": [
    "fuelSummary",
    "t2IncineratorOverprice"
   ],
   "t2|3|elec": [
    "fuelSummary",
    "t2FluidElec"
   ],
   "t2|3|penalty": [
    "fuelSummary",
    "t2FluidPenalty"
   ],
   "t2|3|overprice": [
    "fuelSummary",
    "t2FluidOverprice"
   ],
   "t2|4|elec": [
    "fuelSummary",
    "t2ExtElec"
   ],
   "t2|4|penalty": [
    "fuelSummary",
    "t2ExtPenalty"
   ],
   "t2|4|overprice": [
    "fuelSummary",
    "t2ExtOverprice"
   ],
   "t2|5|elec": [
    "fuelSummary",
    "t2SubtotalElec"
   ],
   "t2|5|penalty": [
    "fuelSummary",
    "t2SubtotalPenalty"
   ],
   "t2|5|overprice": [
    "fuelSummary",
    "t2SubtotalOverprice"
   ],
   "t2|6|elec": [
    "fuelSummary",
    "t2BurnerLngElec"
   ],
   "t2|6|penalty": [
    "fuelSummary",
    "t2BurnerLngPenalty"
   ],
   "t2|6|overprice": [
    "fuelSummary",
    "t2BurnerLngOverprice"
   ],
   "t2|7|elec": [
    "fuelSummary",
    "t2TotalElec"
   ],
   "t2|7|penalty": [
    "fuelSummary",
    "t2TotalPenalty"
   ],
   "t2|7|overprice": [
    "fuelSummary",
    "t2TotalOverprice"
   ]
  }
 }
};
