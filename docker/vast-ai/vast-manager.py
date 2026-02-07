#!/usr/bin/env python3
"""
Vast.ai Cost Efficiency Calculator
Ranks available instances by cost per CPU performance (Cinebench R23 Multi-thread score)
Handles single and dual socket detection automatically.

conda run -n vast-manager python vast-manager.py --rentable-only --cpu-arch amd64 --include-unverified --show-all --show-bids

vastai create instance 30731282 --template_hash 2accb9c2a31d23dfea57e6b4a658a873 --bid_price 0.03 --disk 8

vastai destroy instance 30752186

"""

import os
import requests
import re
from dataclasses import dataclass
from typing import Optional

# Cinebench R23 Multi-thread scores for common CPUs
# Format: regex pattern -> (display_name, core_count, multi_thread_score)
CINEBENCH_R23_SCORES = {
    # AMD EPYC Server CPUs (per socket)
    r"EPYC\s*9654": ("AMD EPYC 9654", 96, 129000),
    r"EPYC\s*9634": ("AMD EPYC 9634", 84, 108000),
    r"EPYC\s*9554": ("AMD EPYC 9554", 64, 96000),
    r"EPYC\s*9474F": ("AMD EPYC 9474F", 48, 72000),
    r"EPYC\s*9354": ("AMD EPYC 9354", 32, 52000),
    r"EPYC\s*7773X": ("AMD EPYC 7773X", 64, 75000),
    r"EPYC\s*7763": ("AMD EPYC 7763", 64, 72000),
    r"EPYC\s*7713": ("AMD EPYC 7713", 64, 68000),
    r"EPYC\s*7662": ("AMD EPYC 7662", 64, 58000),
    r"EPYC\s*7642": ("AMD EPYC 7642", 48, 50000),
    r"EPYC\s*7601": ("AMD EPYC 7601", 32, 22000),
    r"EPYC\s*7551": ("AMD EPYC 7551", 32, 18000),
    r"EPYC\s*7542": ("AMD EPYC 7542", 32, 32000),
    r"EPYC\s*7532": ("AMD EPYC 7532", 32, 35000),
    r"EPYC\s*7502": ("AMD EPYC 7502", 32, 28000),
    r"EPYC\s*7452": ("AMD EPYC 7452", 32, 24000),
    r"EPYC\s*7402": ("AMD EPYC 7402", 24, 18000),
    r"EPYC\s*7352": ("AMD EPYC 7352", 24, 15000),
    r"EPYC\s*7313": ("AMD EPYC 7313", 16, 18500),
    r"EPYC\s*7282": ("AMD EPYC 7282", 16, 13000),
    r"EPYC\s*7252": ("AMD EPYC 7252", 8, 9500),
    r"EPYC\s*7262": ("AMD EPYC 7262", 8, 10000),
    r"EPYC\s*7543": ("AMD EPYC 7543", 32, 42000),
    r"EPYC\s*7B13": ("AMD EPYC 7B13", 64, 70000),
    r"EPYC\s*7R13": ("AMD EPYC 7R13", 48, 55000),
    r"EPYC\s*7R32": ("AMD EPYC 7R32", 48, 52000),
    r"EPYC\s*7V12": ("AMD EPYC 7V12", 64, 60000),
    r"EPYC\s*9254": ("AMD EPYC 9254", 24, 35000),
    r"EPYC\s*9255": ("AMD EPYC 9255", 24, 36000),
    r"EPYC\s*9335": ("AMD EPYC 9335", 32, 48000),
    r"EPYC\s*9374F": ("AMD EPYC 9374F", 32, 55000),
    r"EPYC\s*9384X": ("AMD EPYC 9384X", 32, 60000),
    r"EPYC\s*9534": ("AMD EPYC 9534", 64, 85000),
    r"EPYC\s*9124": ("AMD EPYC 9124", 16, 25000),
    r"EPYC\s*9135": ("AMD EPYC 9135", 16, 26000),
    r"EPYC\s*9175F": ("AMD EPYC 9175F", 16, 32000),
    r"EPYC\s*9224": ("AMD EPYC 9224", 24, 38000),
    r"EPYC\s*9334": ("AMD EPYC 9334", 32, 50000),
    r"EPYC\s*9454": ("AMD EPYC 9454", 48, 72000),
    r"EPYC\s*7V13": ("AMD EPYC 7V13", 64, 70000),
    r"EPYC\s*7Y83": ("AMD EPYC 7Y83", 64, 68000),
    r"EPYC\s*8224P?": ("AMD EPYC 8224P", 24, 28000),
    # Turin EPYC 9000-series (high core count)
    r"EPYC\s*9555": ("AMD EPYC 9555", 64, 95000),
    r"EPYC\s*9684X": ("AMD EPYC 9684X", 96, 120000),
    r"EPYC\s*9734": ("AMD EPYC 9734", 112, 140000),
    r"EPYC\s*9754": ("AMD EPYC 9754", 128, 160000),
    r"EPYC\s*9755": ("AMD EPYC 9755", 128, 165000),
    r"EPYC\s*9965": ("AMD EPYC 9965", 192, 220000),
    r"EPYC\s*9B14": ("AMD EPYC 9B14", 96, 115000),
    r"EPYC\s*7552": ("AMD EPYC 7552", 48, 45000),
    r"EPYC\s*7K83": ("AMD EPYC 7K83", 64, 68000),
    r"EPYC\s*9J14": ("AMD EPYC 9J14", 96, 118000),
    r"EPYC\s*9K65": ("AMD EPYC 9K65", 192, 215000),
    r"EPYC\s*9K84": ("AMD EPYC 9K84", 96, 115000),
    r"EPYC\s*9V33X": ("AMD EPYC 9V33X", 96, 125000),
    # Intel Core i3
    r"i3.?12100[F]?": ("Intel Core i3-12100/F", 4, 8500),
    # AMD Ryzen 5 / Athlon
    r"Ryzen\s*5\s*3400G": ("AMD Ryzen 5 3400G", 4, 5000),
    r"Ryzen\s*5\s*3500": ("AMD Ryzen 5 3500", 6, 6500),
    r"Ryzen\s*5\s*5500": ("AMD Ryzen 5 5500", 6, 11000),
    r"Athlon\s*3000G": ("AMD Athlon 3000G", 2, 2000),
    r"EPYC\s*7281": ("AMD EPYC 7281", 16, 12000),
    r"EPYC\s*7302P?": ("AMD EPYC 7302/P", 16, 16000),
    r"EPYC\s*7343": ("AMD EPYC 7343", 16, 20000),
    r"EPYC\s*7413": ("AMD EPYC 7413", 24, 25000),
    r"EPYC\s*7443P?": ("AMD EPYC 7443/P", 24, 30000),
    r"EPYC\s*7453": ("AMD EPYC 7453", 28, 32000),
    r"EPYC\s*7513": ("AMD EPYC 7513", 32, 38000),
    r"EPYC\s*7573X": ("AMD EPYC 7573X", 32, 48000),
    r"EPYC\s*7643": ("AMD EPYC 7643", 48, 55000),
    r"EPYC\s*7F72": ("AMD EPYC 7F72", 24, 28000),
    r"EPYC\s*7F52": ("AMD EPYC 7F52", 16, 20000),
    r"EPYC\s*7301": ("AMD EPYC 7301", 16, 12000),
    r"EPYC\s*7351P?": ("AMD EPYC 7351/P", 16, 14000),
    r"EPYC\s*7371": ("AMD EPYC 7371", 16, 16000),
    r"EPYC\s*7401P?": ("AMD EPYC 7401/P", 24, 17000),
    r"EPYC\s*7501": ("AMD EPYC 7501", 32, 22000),
    r"EPYC\s*7663": ("AMD EPYC 7663", 56, 62000),
    r"EPYC\s*7702P?": ("AMD EPYC 7702/P", 64, 60000),
    r"EPYC\s*7742": ("AMD EPYC 7742", 64, 65000),
    r"EPYC\s*7B12": ("AMD EPYC 7B12", 64, 62000),
    r"EPYC\s*7C13": ("AMD EPYC 7C13", 64, 68000),
    r"EPYC\s*7D12": ("AMD EPYC 7D12", 32, 35000),
    r"EPYC\s*75F3": ("AMD EPYC 75F3", 32, 45000),
    r"EPYC\s*7H12": ("AMD EPYC 7H12", 64, 70000),
    r"EPYC\s*7J13": ("AMD EPYC 7J13", 64, 72000),
    r"EPYC\s*7K62": ("AMD EPYC 7K62", 48, 50000),
    r"EPYC\s*7R12": ("AMD EPYC 7R12", 48, 48000),
    r"EPYC\s*7R43": ("AMD EPYC 7R43", 64, 62000),
    r"EPYC\s*7T83": ("AMD EPYC 7T83", 64, 68000),
    
    # AMD Threadripper 9000-series (latest desktop HEDT)
    r"Threadripper\s*9970X": ("AMD Threadripper 9970X", 32, 65000),
    r"Threadripper\s*9980X": ("AMD Threadripper 9980X", 64, 120000),
    r"Threadripper\s*1900X": ("AMD Threadripper 1900X", 8, 8000),
    r"Threadripper\s*PRO\s*7945WX": ("AMD Threadripper PRO 7945WX", 12, 25000),
    
    # Older Ryzen
    r"Ryzen\s*7\s*1800X": ("AMD Ryzen 7 1800X", 8, 7500),
    r"Ryzen\s*5\s*7500F": ("AMD Ryzen 5 7500F", 6, 14000),
    r"Ryzen\s*5\s*8500G": ("AMD Ryzen 5 8500G", 6, 12000),
    r"Ryzen\s*9\s*7945HX": ("AMD Ryzen 9 7945HX", 16, 35000),
    r"EPYC\s*9R14": ("AMD EPYC 9R14", 96, 115000),
    r"Ryzen\s*3\s*4100": ("AMD Ryzen 3 4100", 4, 5500),
    r"Threadripper\s*7970X": ("AMD Threadripper 7970X", 32, 60000),
    
    # Intel Core Ultra (Arrow Lake)
    r"Ultra\s*5\s*245K": ("Intel Core Ultra 5 245K", 14, 22000),
    r"Ultra\s*7\s*265K": ("Intel Core Ultra 7 265K", 20, 30000),
    r"Ultra\s*9\s*285K": ("Intel Core Ultra 9 285K", 24, 38000),
    
    # Intel Core i3/i5 older gens
    r"i3.?10100[F]?": ("Intel Core i3-10100/F", 4, 6000),
    r"i3.?10105": ("Intel Core i3-10105", 4, 6500),
    r"i5.?10500": ("Intel Core i5-10500", 6, 8500),
    r"i5.?7500": ("Intel Core i5-7500", 4, 4500),
    r"i5.?9400[F]?": ("Intel Core i5-9400/F", 6, 7000),
    r"i5.?9600K": ("Intel Core i5-9600K", 6, 7500),
    
    # Intel 11th/12th/13th Gen Core (handles ™ symbol)
    r"i7.?11700": ("Intel Core i7-11700", 8, 13500),
    r"i5.?12400[F]?": ("Intel Core i5-12400/F", 6, 13000),
    r"i5.?13400[F]?": ("Intel Core i5-13400/F", 10, 19000),
    r"i5.?13500": ("Intel Core i5-13500", 14, 22000),
    
    # Older Intel Core (6th-7th gen and earlier)
    r"i5.?6600": ("Intel Core i5-6600", 4, 4300),
    r"i7.?3770K?": ("Intel Core i7-3770/K", 4, 4000),
    r"i7.?5930K": ("Intel Core i7-5930K", 6, 6500),
    r"i7.?6700K?": ("Intel Core i7-6700/K", 4, 5200),
    r"i7.?7700": ("Intel Core i7-7700", 4, 5500),
    r"Pentium.*G4560": ("Intel Pentium G4560", 2, 2000),
    
    # AMD Ryzen 7 APU / Threadripper
    r"Ryzen\s*7\s*5700G": ("AMD Ryzen 7 5700G", 8, 15000),
    r"Threadripper\s*9960X": ("AMD Threadripper 9960X", 24, 55000),
    r"Threadripper\s*PRO\s*7995WX": ("AMD Threadripper PRO 7995WX", 96, 140000),
    r"Threadripper\s*PRO\s*7985WX": ("AMD Threadripper PRO 7985WX", 64, 115000),
    r"Threadripper\s*PRO\s*7975WX": ("AMD Threadripper PRO 7975WX", 32, 75000),
    r"Threadripper\s*PRO\s*7965WX": ("AMD Threadripper PRO 7965WX", 24, 58000),
    r"Threadripper\s*PRO\s*7955WX": ("AMD Threadripper PRO 7955WX", 16, 37000),
    r"Threadripper\s*PRO\s*5995WX": ("AMD Threadripper PRO 5995WX", 64, 90000),
    r"Threadripper\s*PRO\s*5975WX": ("AMD Threadripper PRO 5975WX", 32, 50000),
    r"Threadripper\s*PRO\s*5965WX": ("AMD Threadripper PRO 5965WX", 24, 38000),
    r"Threadripper\s*PRO\s*5955WX": ("AMD Threadripper PRO 5955WX", 16, 28000),
    r"Threadripper\s*PRO\s*5945WX": ("AMD Threadripper PRO 5945WX", 12, 22000),
    r"Threadripper\s*PRO\s*3995WX": ("AMD Threadripper PRO 3995WX", 64, 65000),
    r"Threadripper\s*PRO\s*3975WX": ("AMD Threadripper PRO 3975WX", 32, 48000),
    r"Threadripper\s*PRO\s*3955WX": ("AMD Threadripper PRO 3955WX", 16, 25000),
    
    # AMD Ryzen Threadripper (non-PRO)
    r"Threadripper\s*3990X": ("AMD Threadripper 3990X", 64, 75000),
    r"Threadripper\s*3970X": ("AMD Threadripper 3970X", 32, 40000),
    r"Threadripper\s*3960X": ("AMD Threadripper 3960X", 24, 32000),
    r"Threadripper\s*2990WX": ("AMD Threadripper 2990WX", 32, 28000),
    r"Threadripper\s*2970WX": ("AMD Threadripper 2970WX", 24, 22000),
    r"Threadripper\s*2950X": ("AMD Threadripper 2950X", 16, 17000),
    r"Threadripper\s*1950X": ("AMD Threadripper 1950X", 16, 14000),
    
    # AMD Ryzen 9 (single socket consumer/prosumer)
    r"Ryzen\s*9\s*9950X": ("AMD Ryzen 9 9950X", 16, 40000),
    r"Ryzen\s*9\s*9900X": ("AMD Ryzen 9 9900X", 12, 28500),
    r"Ryzen\s*9\s*7950X3D": ("AMD Ryzen 9 7950X3D", 16, 38500),
    r"Ryzen\s*9\s*7950X": ("AMD Ryzen 9 7950X", 16, 39000),
    r"Ryzen\s*9\s*7900X3D": ("AMD Ryzen 9 7900X3D", 12, 27500),
    r"Ryzen\s*9\s*7900X": ("AMD Ryzen 9 7900X", 12, 29000),
    r"Ryzen\s*9\s*7900": ("AMD Ryzen 9 7900", 12, 26000),
    r"Ryzen\s*9\s*5950X": ("AMD Ryzen 9 5950X", 16, 28000),
    r"Ryzen\s*9\s*5900X": ("AMD Ryzen 9 5900X", 12, 21500),
    r"Ryzen\s*9\s*3950X": ("AMD Ryzen 9 3950X", 16, 23000),
    r"Ryzen\s*9\s*3900X": ("AMD Ryzen 9 3900X", 12, 17000),
    
    # AMD Ryzen 7
    r"Ryzen\s*7\s*9800X3D": ("AMD Ryzen 7 9800X3D", 8, 23500),
    r"Ryzen\s*7\s*9700X": ("AMD Ryzen 7 9700X", 8, 20000),
    r"Ryzen\s*7\s*7800X3D": ("AMD Ryzen 7 7800X3D", 8, 18500),
    r"Ryzen\s*7\s*7700X": ("AMD Ryzen 7 7700X", 8, 19700),
    r"Ryzen\s*7\s*7700": ("AMD Ryzen 7 7700", 8, 17500),
    r"Ryzen\s*7\s*5800X3D": ("AMD Ryzen 7 5800X3D", 8, 15000),
    r"Ryzen\s*7\s*5800X": ("AMD Ryzen 7 5800X", 8, 15200),
    r"Ryzen\s*7\s*5800": ("AMD Ryzen 7 5800", 8, 14500),
    r"Ryzen\s*7\s*5700X": ("AMD Ryzen 7 5700X", 8, 14000),
    r"Ryzen\s*7\s*3800X": ("AMD Ryzen 7 3800X", 8, 12000),
    r"Ryzen\s*7\s*3700X": ("AMD Ryzen 7 3700X", 8, 12000),
    
    # AMD Ryzen 5
    r"Ryzen\s*5\s*9600X": ("AMD Ryzen 5 9600X", 6, 15000),
    r"Ryzen\s*5\s*7600X": ("AMD Ryzen 5 7600X", 6, 15200),
    r"Ryzen\s*5\s*7600": ("AMD Ryzen 5 7600", 6, 14000),
    r"Ryzen\s*5\s*5600X": ("AMD Ryzen 5 5600X", 6, 11500),
    r"Ryzen\s*5\s*5600": ("AMD Ryzen 5 5600", 6, 11000),
    r"Ryzen\s*5\s*3600X": ("AMD Ryzen 5 3600X", 6, 9000),
    r"Ryzen\s*5\s*3600": ("AMD Ryzen 5 3600", 6, 8600),
    
    # Intel Xeon W (Workstation) - typically single socket
    r"Xeon\s*[wW]9-3495X": ("Intel Xeon w9-3495X", 56, 72000),
    r"Xeon\s*[wW]9-3475X": ("Intel Xeon w9-3475X", 36, 55000),
    r"Xeon\s*[wW]7-3465X": ("Intel Xeon w7-3465X", 28, 41000),
    r"Xeon\s*[wW]7-3455": ("Intel Xeon w7-3455", 24, 36000),
    r"Xeon\s*[wW]7-2495X": ("Intel Xeon w7-2495X", 24, 35000),
    r"Xeon\s*[wW]5-3435X": ("Intel Xeon w5-3435X", 16, 26000),
    r"Xeon\s*[wW]-3365": ("Intel Xeon W-3365", 32, 33000),
    r"Xeon\s*[wW]-3345": ("Intel Xeon W-3345", 24, 29000),
    r"Xeon\s*[wW]-3323": ("Intel Xeon W-3323", 12, 16000),
    r"Xeon\s*[wW]-3275": ("Intel Xeon W-3275", 28, 28000),
    r"Xeon\s*[wW]-3265": ("Intel Xeon W-3265", 24, 25000),
    r"Xeon\s*[wW]-3245": ("Intel Xeon W-3245", 16, 19000),
    r"Xeon\s*[wW]-3235": ("Intel Xeon W-3235", 12, 17000),
    r"Xeon\s*[wW]-3223": ("Intel Xeon W-3223", 8, 10000),
    r"Xeon\s*[wW]-2295": ("Intel Xeon W-2295", 18, 16500),
    r"Xeon\s*[wW]-2275": ("Intel Xeon W-2275", 14, 14500),
    r"Xeon\s*[wW]-2265": ("Intel Xeon W-2265", 12, 16000),
    r"Xeon\s*[wW]-2255": ("Intel Xeon W-2255", 10, 12500),
    r"Xeon\s*[wW]-2245": ("Intel Xeon W-2245", 8, 10000),
    r"Xeon\s*[wW]-2235": ("Intel Xeon W-2235", 6, 8000),
    r"Xeon\s*[wW]-2225": ("Intel Xeon W-2225", 4, 6000),
    r"Xeon\s*[wW]-2195": ("Intel Xeon W-2195", 18, 20000),
    r"Xeon\s*[wW]-2175": ("Intel Xeon W-2175", 14, 16000),
    r"Xeon\s*[wW]-2155": ("Intel Xeon W-2155", 10, 10000),
    r"Xeon\s*[wW]-2145": ("Intel Xeon W-2145", 8, 9000),
    r"Xeon\s*[wW]-2135": ("Intel Xeon W-2135", 6, 7000),
    r"Xeon\s*[wW]-2125": ("Intel Xeon W-2125", 4, 5000),
    
    # Intel Xeon Scalable (Gold/Platinum) - commonly dual socket in servers
    r"Xeon\s*Platinum\s*8480\+?": ("Intel Xeon Platinum 8480+", 56, 55000),
    r"Xeon\s*Platinum\s*8470": ("Intel Xeon Platinum 8470", 52, 50000),
    r"Xeon\s*Platinum\s*8380": ("Intel Xeon Platinum 8380", 40, 42000),
    r"Xeon\s*Platinum\s*8370C": ("Intel Xeon Platinum 8370C", 32, 38000),
    r"Xeon\s*Platinum\s*8358": ("Intel Xeon Platinum 8358", 32, 35000),
    r"Xeon\s*Platinum\s*8352[VY]?": ("Intel Xeon Platinum 8352", 32, 32000),
    r"Xeon\s*Platinum\s*8280": ("Intel Xeon Platinum 8280", 28, 30000),
    r"Xeon\s*Platinum\s*8275CL": ("Intel Xeon Platinum 8275CL", 24, 28000),
    r"Xeon\s*Platinum\s*8260": ("Intel Xeon Platinum 8260", 24, 25000),
    r"Xeon\s*Platinum\s*8180": ("Intel Xeon Platinum 8180", 28, 24000),
    r"Xeon\s*Platinum\s*8168": ("Intel Xeon Platinum 8168", 24, 22000),
    r"Xeon\s*Platinum\s*8160": ("Intel Xeon Platinum 8160", 24, 20000),
    r"Xeon\s*Platinum\s*8124M": ("Intel Xeon Platinum 8124M", 18, 20000),
    
    r"Xeon\s*Gold\s*6442Y": ("Intel Xeon Gold 6442Y", 24, 33000),
    r"Xeon\s*Gold\s*6430": ("Intel Xeon Gold 6430", 32, 30000),
    r"Xeon\s*Gold\s*6348": ("Intel Xeon Gold 6348", 28, 25000),
    r"Xeon\s*Gold\s*6338": ("Intel Xeon Gold 6338", 32, 24000),
    r"Xeon\s*Gold\s*6330": ("Intel Xeon Gold 6330", 28, 22000),
    r"Xeon\s*Gold\s*6326": ("Intel Xeon Gold 6326", 16, 18000),
    r"Xeon\s*Gold\s*6248R?": ("Intel Xeon Gold 6248", 24, 18500),
    r"Xeon\s*Gold\s*6242": ("Intel Xeon Gold 6242", 16, 15000),
    r"Xeon\s*Gold\s*6240": ("Intel Xeon Gold 6240", 18, 15000),
    r"Xeon\s*Gold\s*6238": ("Intel Xeon Gold 6238", 22, 18000),
    r"Xeon\s*Gold\s*6230": ("Intel Xeon Gold 6230", 20, 14500),
    r"Xeon\s*Gold\s*6226R?": ("Intel Xeon Gold 6226", 12, 13000),
    r"Xeon\s*Gold\s*6154": ("Intel Xeon Gold 6154", 18, 15000),
    r"Xeon\s*Gold\s*6152": ("Intel Xeon Gold 6152", 22, 17000),
    r"Xeon\s*Gold\s*6150": ("Intel Xeon Gold 6150", 18, 14500),
    r"Xeon\s*Gold\s*6148": ("Intel Xeon Gold 6148", 20, 13500),
    r"Xeon\s*Gold\s*6142": ("Intel Xeon Gold 6142", 16, 13000),
    r"Xeon\s*Gold\s*6140": ("Intel Xeon Gold 6140", 18, 12500),
    r"Xeon\s*Gold\s*6138": ("Intel Xeon Gold 6138", 20, 12000),
    r"Xeon\s*Gold\s*6136": ("Intel Xeon Gold 6136", 12, 11500),
    r"Xeon\s*Gold\s*6134": ("Intel Xeon Gold 6134", 8, 9000),
    r"Xeon\s*Gold\s*6132": ("Intel Xeon Gold 6132", 14, 10000),
    r"Xeon\s*Gold\s*6130": ("Intel Xeon Gold 6130", 16, 10000),
    r"Xeon\s*Gold\s*6126": ("Intel Xeon Gold 6126", 12, 8000),
    r"Xeon\s*Gold\s*5220": ("Intel Xeon Gold 5220", 18, 12000),
    r"Xeon\s*Gold\s*5218": ("Intel Xeon Gold 5218", 16, 10500),
    r"Xeon\s*Gold\s*5217": ("Intel Xeon Gold 5217", 8, 8500),
    r"Xeon\s*Gold\s*5215": ("Intel Xeon Gold 5215", 10, 7000),
    
    r"Xeon\s*Silver\s*4316": ("Intel Xeon Silver 4316", 20, 14500),
    r"Xeon\s*Silver\s*4314": ("Intel Xeon Silver 4314", 16, 13000),
    r"Xeon\s*Silver\s*4310": ("Intel Xeon Silver 4310", 12, 12000),
    r"Xeon\s*Silver\s*4216": ("Intel Xeon Silver 4216", 16, 10000),
    r"Xeon\s*Silver\s*4214": ("Intel Xeon Silver 4214", 12, 8500),
    r"Xeon\s*Silver\s*4210": ("Intel Xeon Silver 4210", 10, 6500),
    r"Xeon\s*Silver\s*4116": ("Intel Xeon Silver 4116", 12, 7000),
    r"Xeon\s*Silver\s*4114": ("Intel Xeon Silver 4114", 10, 6000),
    r"Xeon\s*Silver\s*4110": ("Intel Xeon Silver 4110", 8, 5000),
    
    # Intel Xeon E5/E7 (older, commonly dual socket)
    r"Xeon\s*E5-2699[Av]*\s*v?4": ("Intel Xeon E5-2699 v4", 22, 17000),
    r"Xeon\s*E5-2697[Av]*\s*v?4": ("Intel Xeon E5-2697 v4", 18, 15500),
    r"Xeon\s*E5-2696[Av]*\s*v?4": ("Intel Xeon E5-2696 v4", 22, 16000),
    r"Xeon\s*E5-2695[Av]*\s*v?4": ("Intel Xeon E5-2695 v4", 18, 14000),
    r"Xeon\s*E5-2690[Av]*\s*v?4": ("Intel Xeon E5-2690 v4", 14, 12500),
    r"Xeon\s*E5-2680[Av]*\s*v?4": ("Intel Xeon E5-2680 v4", 14, 12000),
    r"Xeon\s*E5-2678[Av]*\s*v?3": ("Intel Xeon E5-2678 v3", 12, 9500),
    r"Xeon\s*E5-2670[Av]*\s*v?3": ("Intel Xeon E5-2670 v3", 12, 9000),
    r"Xeon\s*E5-2660[Av]*\s*v?4": ("Intel Xeon E5-2660 v4", 14, 10000),
    r"Xeon\s*E5-2650[Av]*\s*v?4": ("Intel Xeon E5-2650 v4", 12, 9000),
    r"Xeon\s*E5-2640[Av]*\s*v?4": ("Intel Xeon E5-2640 v4", 10, 7500),
    r"Xeon\s*E5-2630[Av]*\s*v?4": ("Intel Xeon E5-2630 v4", 10, 6500),
    r"Xeon\s*E5-2620[Av]*\s*v?4": ("Intel Xeon E5-2620 v4", 8, 5500),
    r"E5.?1650.*v2": ("Intel Xeon E5-1650 v2", 6, 5000),
    r"E5.?2609.*v3": ("Intel Xeon E5-2609 v3", 6, 3500),
    r"E5.?2620.*v2": ("Intel Xeon E5-2620 v2", 6, 4000),
    r"E5.?2620.*v3": ("Intel Xeon E5-2620 v3", 6, 4500),
    r"E5.?2650.*v3": ("Intel Xeon E5-2650 v3", 10, 8000),
    r"E5.?2650\s*0": ("Intel Xeon E5-2650", 8, 4500),
    r"E5.?2640\s*0": ("Intel Xeon E5-2640", 6, 3800),
    r"E5.?2630.*v4": ("Intel Xeon E5-2630 v4", 10, 6500),
    r"E5.?2650.*v4": ("Intel Xeon E5-2650 v4", 12, 9500),
    r"E5.?2658A.*v3": ("Intel Xeon E5-2658A v3", 12, 8500),
    r"E5.?2660.*v3": ("Intel Xeon E5-2660 v3", 10, 8000),
    r"E5.?2660.*v4": ("Intel Xeon E5-2660 v4", 14, 10500),
    # Additional Core i5/i7 older
    r"i5.?4460": ("Intel Core i5-4460", 4, 3000),
    r"i7.?10700": ("Intel Core i7-10700", 8, 12500),
    r"i9.?13900F": ("Intel Core i9-13900F", 24, 38000),
    # More Xeon E5 v3/v4
    r"E5.?2620.*v4": ("Intel Xeon E5-2620 v4", 8, 5500),
    r"E5.?2667.*v3": ("Intel Xeon E5-2667 v3", 8, 9500),
    r"E5.?2670.*v3": ("Intel Xeon E5-2670 v3", 12, 10000),
    r"E5.?2673.*v4": ("Intel Xeon E5-2673 v4", 20, 14000),
    r"E5.?2676.*v3": ("Intel Xeon E5-2676 v3", 12, 10000),
    r"E5.?2678.*v3": ("Intel Xeon E5-2678 v3", 12, 10500),
    r"E5.?2680.*v3": ("Intel Xeon E5-2680 v3", 12, 11000),
    r"E5.?2673.*v3": ("Intel Xeon E5-2673 v3", 12, 10500),
    r"E5.?2682.*v4": ("Intel Xeon E5-2682 v4", 16, 12000),
    r"E5.?2683.*v3": ("Intel Xeon E5-2683 v3", 14, 12500),
    r"E5.?2683.*v4": ("Intel Xeon E5-2683 v4", 16, 14000),
    r"E5.?2687W.*v4": ("Intel Xeon E5-2687W v4", 12, 13000),
    r"E5.?2687W\s*0": ("Intel Xeon E5-2687W", 8, 8000),
    r"E5.?2690\s*0": ("Intel Xeon E5-2690", 8, 8500),
    r"E5.?2690.*v3": ("Intel Xeon E5-2690 v3", 12, 11500),
    r"E5.?2690.*v4": ("Intel Xeon E5-2690 v4", 14, 14000),
    r"Xeon\s*Phi.*7250": ("Intel Xeon Phi 7250", 68, 18000),
    # AMD Threadripper 2000-series
    r"Threadripper\s*2920X": ("AMD Threadripper 2920X", 12, 15000),
    r"Threadripper\s*2950X": ("AMD Threadripper 2950X", 16, 22000),
    r"Threadripper\s*2970WX": ("AMD Threadripper 2970WX", 24, 28000),
    r"Threadripper\s*2990WX": ("AMD Threadripper 2990WX", 32, 32000),
    # More Xeon E5
    r"E5.?1660.*v3": ("Intel Xeon E5-1660 v3", 8, 9000),
    r"E5.?2690.*v2": ("Intel Xeon E5-2690 v2", 10, 8000),
    r"E5.?2695.*v2": ("Intel Xeon E5-2695 v2", 12, 9500),
    r"E5.?2695.*v3": ("Intel Xeon E5-2695 v3", 14, 13000),
    r"E5.?2695.*v4": ("Intel Xeon E5-2695 v4", 18, 15500),
    r"E5.?2696.*v3": ("Intel Xeon E5-2696 v3", 18, 15500),
    r"E5.?2696.*v4": ("Intel Xeon E5-2696 v4", 22, 18000),
    r"E5.?2697.*v2": ("Intel Xeon E5-2697 v2", 12, 10000),
    r"E5.?2697.*v3": ("Intel Xeon E5-2697 v3", 14, 13500),
    r"E5.?2697.*v4": ("Intel Xeon E5-2697 v4", 18, 16000),
    r"E5.?2698.*v3": ("Intel Xeon E5-2698 v3", 16, 15000),
    r"E5.?2698.*v4": ("Intel Xeon E5-2698 v4", 20, 17500),
    r"E5.?2699.*v3": ("Intel Xeon E5-2699 v3", 18, 16500),
    r"E5.?2699.*v4": ("Intel Xeon E5-2699 v4", 22, 19000),
    # Intel Core i7-8xxx
    r"i7.?8700": ("Intel Core i7-8700", 6, 9500),
    r"i7.?8086K": ("Intel Core i7-8086K", 6, 10000),
    # AMD Ryzen 5
    r"Ryzen\s*5\s*8400F": ("AMD Ryzen 5 8400F", 6, 13500),
    # Xeon Gold 6100-series (1st/2nd Gen Scalable)
    r"Gold\s*6130": ("Intel Xeon Gold 6130", 16, 14000),
    r"Gold\s*6132": ("Intel Xeon Gold 6132", 14, 13000),
    r"Gold\s*6133": ("Intel Xeon Gold 6133", 20, 17500),
    r"Gold\s*6138": ("Intel Xeon Gold 6138", 20, 19000),
    r"Gold\s*6140": ("Intel Xeon Gold 6140", 18, 18000),
    r"Gold\s*6148": ("Intel Xeon Gold 6148", 20, 20000),
    r"Gold\s*6150": ("Intel Xeon Gold 6150", 18, 18500),
    r"Gold\s*6154": ("Intel Xeon Gold 6154", 18, 19500),
    # Xeon E5-4xxx / E7-xxxx
    r"E5.?4667.*v4": ("Intel Xeon E5-4667 v4", 18, 16000),
    r"E7.?4880.*v2": ("Intel Xeon E7-4880 v2", 15, 13000),
    r"E7.?8890.*v4": ("Intel Xeon E7-8890 v4", 24, 22000),
    r"E5.?2640.*v4": ("Intel Xeon E5-2640 v4", 10, 7500),
    r"E5.?1620.*v4": ("Intel Xeon E5-1620 v4", 4, 6000),
    # Xeon Gold 62xx series (2nd/3rd Gen Scalable)
    r"Gold\s*6146": ("Intel Xeon Gold 6146", 12, 15000),
    r"Gold\s*6226": ("Intel Xeon Gold 6226", 12, 14500),
    r"Gold\s*6226R": ("Intel Xeon Gold 6226R", 16, 18000),
    r"Gold\s*6242": ("Intel Xeon Gold 6242", 16, 18500),
    r"Gold\s*6244": ("Intel Xeon Gold 6244", 8, 12000),
    r"Gold\s*6248R?": ("Intel Xeon Gold 6248/R", 20, 21000),
    r"Gold\s*6271C": ("Intel Xeon Gold 6271C", 24, 25000),
    r"Gold\s*6252": ("Intel Xeon Gold 6252", 24, 23000),
    r"Gold\s*6254": ("Intel Xeon Gold 6254", 18, 20500),
    r"Gold\s*6258R": ("Intel Xeon Gold 6258R", 28, 27000),
    r"Gold\s*6262V": ("Intel Xeon Gold 6262V", 24, 22000),
    # Xeon Gold 63xx/64xx (3rd/4th Gen Ice Lake/Sapphire Rapids)
    r"Gold\s*6330": ("Intel Xeon Gold 6330", 28, 32000),
    r"Gold\s*6338": ("Intel Xeon Gold 6338", 32, 38000),
    r"Gold\s*6342": ("Intel Xeon Gold 6342", 24, 29000),
    r"Gold\s*6430": ("Intel Xeon Gold 6430", 32, 42000),
    r"Gold\s*6438M": ("Intel Xeon Gold 6438M", 32, 45000),
    r"Gold\s*6448Y": ("Intel Xeon Gold 6448Y", 32, 48000),
    # AMD Threadripper 1000-series
    r"Threadripper\s*1920X": ("AMD Threadripper 1920X", 12, 15000),
    r"Threadripper\s*1950X": ("AMD Threadripper 1950X", 16, 20000),
    # Intel Core i5 misc
    r"i5.?7400": ("Intel Core i5-7400", 4, 4200),
    # Xeon Platinum Cloud/Azure/AWS variants
    r"Platinum\s*8272CL?": ("Intel Xeon Platinum 8272CL", 26, 32000),
    r"Platinum\s*8273CL": ("Intel Xeon Platinum 8273CL", 26, 33000),
    r"Platinum\s*8272L": ("Intel Xeon Platinum 8272L", 26, 32000),
    r"Platinum\s*8352V": ("Intel Xeon Platinum 8352V", 36, 45000),
    r"Platinum\s*8368": ("Intel Xeon Platinum 8368", 38, 52000),
    r"Platinum\s*8462Y\+?": ("Intel Xeon Platinum 8462Y+", 32, 45000),
    r"Platinum\s*8470": ("Intel Xeon Platinum 8470", 52, 60000),
    r"Platinum\s*8480\+?": ("Intel Xeon Platinum 8480+", 56, 68000),
    r"Platinum\s*8468": ("Intel Xeon Platinum 8468", 48, 58000),
    r"Platinum\s*8468V": ("Intel Xeon Platinum 8468V", 48, 55000),
    r"Platinum\s*8469C": ("Intel Xeon Platinum 8469C", 24, 35000),
    r"Platinum\s*8481C": ("Intel Xeon Platinum 8481C", 56, 65000),
    # Xeon Silver (common in Vast.ai)
    r"Silver\s*4110": ("Intel Xeon Silver 4110", 8, 6500),
    r"Silver\s*4116": ("Intel Xeon Silver 4116", 12, 8000),
    r"Silver\s*4210": ("Intel Xeon Silver 4210", 10, 7500),
    r"Silver\s*4310": ("Intel Xeon Silver 4310", 12, 12000),
    r"Silver\s*4314": ("Intel Xeon Silver 4314", 16, 14500),
    r"Silver\s*4316": ("Intel Xeon Silver 4316", 20, 17000),
    # Xeon E5-2680 v2 (older, but still common)
    r"E5.?2680.*v2": ("Intel Xeon E5-2680 v2", 10, 7000),
    # More Xeon W patterns (with ® handling)
    r"W.?2123": ("Intel Xeon W-2123", 4, 5500),
    r"W.?2133": ("Intel Xeon W-2133", 6, 7500),
    r"W.?2145": ("Intel Xeon W-2145", 8, 9000),
    r"W.?2175": ("Intel Xeon W-2175", 14, 16000),
    r"w7.?3445": ("Intel Xeon w7-3445", 24, 28000),
    r"Silver\s*4114": ("Intel Xeon Silver 4114", 10, 7000),
    r"Silver\s*4214R?": ("Intel Xeon Silver 4214/R", 12, 9000),
    r"W.?2155": ("Intel Xeon W-2155", 10, 11000),
    r"Pentium.*G6400": ("Intel Pentium Gold G6400", 2, 3000),
    # AMD Ryzen 3 / Ryzen 5 APU
    r"Ryzen\s*3\s*1200": ("AMD Ryzen 3 1200", 4, 3500),
    r"Ryzen\s*5\s*2400G": ("AMD Ryzen 5 2400G", 4, 5500),
    # Last remaining Xeons
    r"Platinum\s*8160": ("Intel Xeon Platinum 8160", 24, 28000),
    r"W.?3223": ("Intel Xeon W-3223", 8, 10000),
    # Intel Core i7
    r"i7.?14700F": ("Intel Core i7-14700F", 20, 33000),
    r"i9-14900K[SF]*": ("Intel Core i9-14900KS/KF/K", 24, 41000),
    r"i9-13900K[SF]*": ("Intel Core i9-13900KS/KF/K", 24, 40000),
    r"i9-12900K[SF]*": ("Intel Core i9-12900KS/KF/K", 16, 27000),
    r"i9-11900K[F]*": ("Intel Core i9-11900KF/K", 8, 16000),
    r"i9-10900K[F]*": ("Intel Core i9-10900KF/K", 10, 17500),
    r"i9-10850K": ("Intel Core i9-10850K", 10, 16500),
    r"i9-9900K[SF]*": ("Intel Core i9-9900KS/KF/K", 8, 12500),
    
    # Intel Core i9 (HEDT) - single socket
    r"i9-10980XE": ("Intel Core i9-10980XE", 18, 26000),
    r"i9-10940X": ("Intel Core i9-10940X", 14, 20000),
    r"i9-10920X": ("Intel Core i9-10920X", 12, 18500),
    r"i9-10900X": ("Intel Core i9-10900X", 10, 15500),
    r"i9-9980XE": ("Intel Core i9-9980XE", 18, 23000),
    r"i9-9960X": ("Intel Core i9-9960X", 16, 20000),
    r"i9-9940X": ("Intel Core i9-9940X", 14, 17000),
    r"i9-9920X": ("Intel Core i9-9920X", 12, 15000),
    r"i9-9900X": ("Intel Core i9-9900X", 10, 14000),
    r"i9-7980XE": ("Intel Core i9-7980XE", 18, 22000),
    r"i9-7960X": ("Intel Core i9-7960X", 16, 18500),
    r"i9-7940X": ("Intel Core i9-7940X", 14, 16500),
    r"i9-7920X": ("Intel Core i9-7920X", 12, 15000),
    r"i9-7900X": ("Intel Core i9-7900X", 10, 13000),
    
    # Intel Xeon 5th Gen Scalable (Emerald Rapids / Granite Rapids)
    r"Gold\s*6530": ("Intel Xeon Gold 6530", 32, 42000),
    r"Gold\s*6538Y\+?": ("Intel Xeon Gold 6538Y+", 32, 45000),
    r"Gold\s*6554S": ("Intel Xeon Gold 6554S", 36, 48000),
    r"Platinum\s*8558": ("Intel Xeon Platinum 8558", 48, 58000),
    r"Platinum\s*8570": ("Intel Xeon Platinum 8570", 56, 68000),
    r"Platinum\s*8580": ("Intel Xeon Platinum 8580", 60, 72000),
    r"Platinum\s*8581C": ("Intel Xeon Platinum 8581C", 56, 70000),
    r"6960P": ("Intel Xeon 6960P", 72, 85000),
    r"Pentium.*G5420": ("Intel Pentium Gold G5420", 2, 2500),
    r"i7-14700K[F]*": ("Intel Core i7-14700KF/K", 20, 35000),
    r"i7-13700K[F]*": ("Intel Core i7-13700KF/K", 16, 31000),
    r"i7-12700K[F]*": ("Intel Core i7-12700KF/K", 12, 22000),
    r"i7-11700K[F]*": ("Intel Core i7-11700KF/K", 8, 14500),
    r"i7-10700K[F]*": ("Intel Core i7-10700KF/K", 8, 12500),
    r"i7-9700K[F]*": ("Intel Core i7-9700KF/K", 8, 9500),
    r"i7-8700K": ("Intel Core i7-8700K", 6, 9000),
    r"i7-7820X": ("Intel Core i7-7820X", 8, 10000),
    r"i7-7800X": ("Intel Core i7-7800X", 6, 8000),
    r"i7-6950X": ("Intel Core i7-6950X", 10, 14000),
    r"i7-6900K": ("Intel Core i7-6900K", 8, 12500),
    r"i7-6850K": ("Intel Core i7-6850K", 6, 8500),
    r"i7-6800K": ("Intel Core i7-6800K", 6, 8000),
    
    # Intel Core i5
    r"i5-14600K[F]*": ("Intel Core i5-14600KF/K", 14, 24500),
    r"i5-13600K[F]*": ("Intel Core i5-13600KF/K", 14, 24000),
    r"i5-12600K[F]*": ("Intel Core i5-12600KF/K", 10, 17000),
    r"i5-11600K[F]*": ("Intel Core i5-11600KF/K", 6, 11500),
    r"i5-10600K[F]*": ("Intel Core i5-10600KF/K", 6, 9500),
    r"i5-11400[F]*": ("Intel Core i5-11400F", 6, 10500),
    r"i7-12700\b": ("Intel Core i7-12700", 12, 20000),
    
    # Additional AMD EPYC (newer/cloud variants)
    r"EPYC\s*9655": ("AMD EPYC 9655", 96, 135000),
    r"EPYC\s*9B45": ("AMD EPYC 9B45", 64, 95000),
    r"EPYC\s*9V74": ("AMD EPYC 9V74", 80, 110000),
    r"EPYC\s*9V84": ("AMD EPYC 9V84", 96, 130000),
    
    # Intel Xeon Platinum (5th Gen - Emerald Rapids)
    r"Platinum\s*8568Y\+?": ("Intel Xeon Platinum 8568Y+", 48, 60000),
    r"Platinum\s*8573C": ("Intel Xeon Platinum 8573C", 32, 45000),
    r"Platinum\s*8592\+?": ("Intel Xeon Platinum 8592+", 64, 70000),
    
    # AMD Ryzen (older)
    r"Ryzen\s*5\s*2600": ("AMD Ryzen 5 2600", 6, 6500),
    r"Ryzen\s*5\s*2600X": ("AMD Ryzen 5 2600X", 6, 7000),
    r"Ryzen\s*7\s*2700X": ("AMD Ryzen 7 2700X", 8, 9000),
    r"Ryzen\s*7\s*2700": ("AMD Ryzen 7 2700", 8, 8500),
    
    # AMD Threadripper PRO (additional)
    r"Threadripper\s*PRO\s*3945WX": ("AMD Threadripper PRO 3945WX", 12, 18000),
    
    # Additional missing CPUs discovered from Vast.ai (handles ® and ™ chars)
    r"i5.?10400": ("Intel Core i5-10400F", 6, 9000),
    r"i5.?14500": ("Intel Core i5-14500", 14, 22000),
    r"6767P": ("Intel Xeon 6767P", 48, 55000),
    r"6747P": ("Intel Xeon 6747P", 32, 42000),
    r"E5.?2650.*v2": ("Intel Xeon E5-2650 v2", 8, 5500),
    r"E5.?2680.*v4": ("Intel Xeon E5-2680 v4", 14, 12000),
    r"E5.?2686.*v4": ("Intel Xeon E5-2686 v4", 18, 16000),
    r"Gold\s*5115": ("Intel Xeon Gold 5115", 10, 8000),
    r"Platinum\s*8480C": ("Intel Xeon Platinum 8480C", 56, 54000),
    
    # Additional CPUs from Vast.ai discovered 2026-01
    # Intel Core i3 / i5 / i7 / i9 (older)
    r"i3.?9100F?": ("Intel Core i3-9100/F", 4, 3900),
    r"i3.?8100": ("Intel Core i3-8100", 4, 3600),
    r"i3.?6100": ("Intel Core i3-6100", 2, 2400),
    r"i3.?7100": ("Intel Core i3-7100", 2, 2500),
    r"i3.?14100F?": ("Intel Core i3-14100/F", 4, 10000),
    r"i3.?13100F?": ("Intel Core i3-13100/F", 4, 9500),
    r"i5.?6500": ("Intel Core i5-6500", 4, 3100),
    r"i5.?6400": ("Intel Core i5-6400", 4, 2800),
    r"i5.?5600X?": ("Intel Core i5-5600X", 6, 11500),
    r"i5.?8500": ("Intel Core i5-8500", 6, 5600),
    r"i5.?8600K": ("Intel Core i5-8600K", 6, 6800),
    r"i5.?3470": ("Intel Core i5-3470", 4, 2560),
    r"i5.?14400[FT]?": ("Intel Core i5-14400/F/T", 10, 18000),
    r"i7.?11800H": ("Intel Core i7-11800H", 8, 13000),
    r"i7.?4790K?": ("Intel Core i7-4790/K", 4, 4500),
    r"i7.?4930K": ("Intel Core i7-4930K", 6, 6500),
    r"i7.?5820K": ("Intel Core i7-5820K", 6, 5700),
    r"i7.?5960X": ("Intel Core i7-5960X", 8, 8000),
    r"i7.?14700\b": ("Intel Core i7-14700", 20, 33000),
    r"11th Gen.*i9.?11900\b": ("Intel Core i9-11900", 8, 16000),
    r"12th Gen.*i7.?12700F": ("Intel Core i7-12700F", 12, 20000),
    r"13th Gen.*i7.?13700\b": ("Intel Core i7-13700", 16, 30000),
    
    # Intel Celeron / Pentium
    r"Celeron.*G5905": ("Intel Celeron G5905", 2, 1650),
    r"Celeron.*G4930": ("Intel Celeron G4930", 2, 1200),
    r"Celeron.*G5900": ("Intel Celeron G5900", 2, 1300),
    r"Celeron.*3855U": ("Intel Celeron 3855U", 2, 600),
    r"Pentium.*G3220": ("Intel Pentium G3220", 2, 1000),
    r"Pentium.*G4400": ("Intel Pentium G4400", 2, 1200),
    
    # Intel Xeon E3/E5 (additional)
    r"E3.?1231.*v3": ("Intel Xeon E3-1231 v3", 4, 4500),
    r"E3.?1225.*v3": ("Intel Xeon E3-1225 v3", 4, 3500),
    r"E3.?1270.*v5": ("Intel Xeon E3-1270 v5", 4, 6000),
    r"E.?2174G": ("Intel Xeon E-2174G", 4, 6500),
    r"E5.?1630.*v4": ("Intel Xeon E5-1630 v4", 4, 5500),
    r"E5.?1650.*v4": ("Intel Xeon E5-1650 v4", 6, 7000),
    r"E5.?2630.*v2": ("Intel Xeon E5-2630 v2", 6, 4000),
    r"E5.?2643.*v4": ("Intel Xeon E5-2643 v4", 6, 8000),
    r"E5.?2648L.*0": ("Intel Xeon E5-2648L", 8, 5000),
    r"E5.?2666.*v3": ("Intel Xeon E5-2666 v3", 10, 9700),
    r"E5.?2696.*v2": ("Intel Xeon E5-2696 v2", 12, 10000),
    r"X5670": ("Intel Xeon X5670", 6, 3000),
    
    # Intel Xeon Gold/Platinum 4th/5th gen
    r"Gold\s*5118": ("Intel Xeon Gold 5118", 12, 10000),
    r"Gold\s*5220R": ("Intel Xeon Gold 5220R", 24, 22000),
    r"Gold\s*5317": ("Intel Xeon Gold 5317", 12, 14000),
    r"Gold\s*5416S": ("Intel Xeon Gold 5416S", 16, 18000),
    r"Gold\s*5418Y": ("Intel Xeon Gold 5418Y", 24, 28000),
    r"Gold\s*6434": ("Intel Xeon Gold 6434", 8, 12000),
    r"6548Y\+?": ("Intel Xeon Gold 6548Y+", 32, 42000),
    r"Platinum\s*8163": ("Intel Xeon Platinum 8163", 24, 24000),
    r"Platinum\s*8490H": ("Intel Xeon Platinum 8490H", 60, 80000),
    r"Silver\s*4410Y": ("Intel Xeon Silver 4410Y", 12, 14000),
    
    # AMD Ryzen (additional)
    r"Ryzen\s*3\s*3300X": ("AMD Ryzen 3 3300X", 4, 6800),
    r"Ryzen\s*3\s*PRO\s*4350G": ("AMD Ryzen 3 PRO 4350G", 4, 5500),
    r"Ryzen\s*5\s*4500": ("AMD Ryzen 5 4500", 6, 8500),
    r"Ryzen\s*7\s*5700\b": ("AMD Ryzen 7 5700", 8, 14000),
    r"Ryzen\s*7\s*8700F": ("AMD Ryzen 7 8700F", 8, 18000),
    r"Ryzen\s*9\s*5900\b": ("AMD Ryzen 9 5900", 12, 21000),
    r"Ryzen\s*9\s*9955HX": ("AMD Ryzen 9 9955HX", 16, 30000),
    
    # AMD Threadripper PRO 9000 series
    r"Threadripper\s*PRO\s*9955WX": ("AMD Threadripper PRO 9955WX", 16, 42000),
    r"Threadripper\s*PRO\s*9975WX": ("AMD Threadripper PRO 9975WX", 32, 80000),
    r"Threadripper\s*PRO\s*9995WX": ("AMD Threadripper PRO 9995WX", 96, 175000),
    r"Threadripper\s*7960X": ("AMD Threadripper 7960X", 24, 55000),
    
    # AMD EPYC (additional)
    r"EPYC\s*7F32": ("AMD EPYC 7F32", 8, 12500),
    r"EPYC\s*9115": ("AMD EPYC 9115", 16, 25000),
    r"EPYC\s*9455": ("AMD EPYC 9455", 48, 75000),
    r"EPYC\s*4585PX": ("AMD EPYC 4585PX", 16, 25000),
    r"EPYC.?Rome": ("AMD EPYC Rome", 64, 50000),
    
    # Misc
    r"FX.?9590": ("AMD FX-9590", 8, 3500),
    r"Athlon\s*300GE": ("AMD Athlon 300GE", 2, 1500),
    r"Xeon\s*Phi.*7210": ("Intel Xeon Phi 7210", 64, 12000),
    
    # Additional discovered 2026-01 round 2
    r"E5.?2620\s*0": ("Intel Xeon E5-2620", 6, 3500),
    r"E5.?2670.*v2": ("Intel Xeon E5-2670 v2", 10, 6500),
    r"E7.?8880.*v4": ("Intel Xeon E7-8880 v4", 22, 20000),
    r"Gold\s*6122": ("Intel Xeon Gold 6122", 8, 7500),
    r"Threadripper\s*PRO\s*9985WX": ("AMD Threadripper PRO 9985WX", 64, 120000),
    r"E5.?2660.*v2": ("Intel Xeon E5-2660 v2", 10, 7000),
}


@dataclass
class VastInstance:
    """Represents a Vast.ai instance offer"""
    id: int
    cpu_name: str
    cpu_cores: int
    cpu_cores_effective: float
    cpu_ghz: float
    dph_total: float  # Dollars per hour (on-demand)
    min_bid: float  # Minimum bid price (interruptible)
    ram_gb: float
    disk_gb: float
    reliability: float
    inet_down: float
    inet_up: float
    
    # Template compatibility fields
    cuda_max_good: float = 0.0
    cpu_arch: str = ""
    rentable: bool = False
    
    # Computed fields
    cinebench_match: Optional[str] = None
    cpu_spec_cores: Optional[int] = None  # Cores per socket from spec
    socket_count: int = 1
    cinebench_score_single: Optional[int] = None  # Score per socket
    cinebench_score_total: Optional[int] = None   # Total score (adjusted for sockets)
    cost_per_cinebench_point: Optional[float] = None
    cost_per_cinebench_point_bid: Optional[float] = None  # Cost efficiency at min_bid price


def lookup_cinebench_score(cpu_name: str) -> tuple[Optional[str], Optional[int], Optional[int]]:
    """
    Look up the Cinebench R23 multi-thread score for a given CPU name.
    Returns (matched_name, core_count, score) or (None, None, None) if not found.
    """
    if not cpu_name:
        return None, None, None
    
    for pattern, (display_name, cores, score) in CINEBENCH_R23_SCORES.items():
        if re.search(pattern, cpu_name, re.IGNORECASE):
            return display_name, cores, score
    
    return None, None, None


def infer_socket_count(vast_cores: int, spec_cores: int) -> int:
    """
    Infer the number of CPU sockets based on comparing Vast's reported
    core count to the CPU's per-socket core count.
    
    Returns 1, 2, or 4 (common server configurations).
    Uses a tolerance to handle slight variations in core reporting.
    """
    if not spec_cores or spec_cores <= 0:
        return 1
    
    ratio = vast_cores / spec_cores
    
    # Conservative logic assuming SMT (threads) is reported by Vast.ai
    # Ratio ~1.0 -> 1 Socket (No SMT)
    # Ratio ~2.0 -> 1 Socket (SMT) - previously inferred as 2 sockets
    # Ratio ~4.0 -> 2 Sockets (SMT)
    # Ratio ~8.0 -> 4 Sockets (SMT)
    
    if ratio <= 2.8:
        return 1
    elif ratio <= 6.5:
        return 2
    else:
        return 4


def fetch_vast_offers(api_key: Optional[str] = None, limit: int = 5000, verified: bool = True) -> list[dict]:
    """
    Fetch available offers from Vast.ai using POST with query body.
    This is the same approach used by the vastai CLI to get all offers.
    
    :param verified: If True (default), filter for only verified hosts. If False, include all.
    """
    url = "https://console.vast.ai/api/v0/bundles/"
    
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    
    # Query format matching the vastai CLI
    # Note: We're NOT filtering by rentable/rented here to get ALL offers
    query = {
        "external": {"eq": False},
        "order": [["dph_total", "asc"]],
        "type": "ask",
        "limit": limit,
        "allocated_storage": 5.0
    }

    if verified:
        query["verified"] = {"eq": True}
    
    try:
        response = requests.post(url, headers=headers, json=query, timeout=60)
        response.raise_for_status()
        data = response.json()
        return data.get("offers", [])
    except requests.exceptions.RequestException as e:
        print(f"Error fetching Vast.ai offers: {e}")
        return []


def process_offers(offers: list[dict]) -> list[VastInstance]:
    """Process raw offers into VastInstance objects with Cinebench data and socket detection."""
    instances = []
    
    for offer in offers:
        cpu_name = offer.get("cpu_name", "")
        matched_name, spec_cores, cb_score = lookup_cinebench_score(cpu_name)
        
        vast_cores = offer.get("cpu_cores", 0)
        vast_cores_effective = offer.get("cpu_cores_effective", 0)
        dph = offer.get("dph_total", 0)
        min_bid = offer.get("min_bid", 0)
        
        # Infer socket count
        socket_count = 1
        total_score = cb_score
        
        if spec_cores and cb_score and vast_cores > 0:
            socket_count = infer_socket_count(vast_cores, spec_cores)
            # Calculate full machine score
            machine_total_score = cb_score * socket_count
            
            # Adjust score based on the fraction of the machine allocated to this offer
            # If cpu_cores_effective is provided and less than total cpu_cores, scale down
            if vast_cores_effective > 0 and vast_cores_effective < vast_cores:
                fraction = vast_cores_effective / vast_cores
                total_score = int(machine_total_score * fraction)
            else:
                total_score = machine_total_score
        
        # Calculate cost per Cinebench point (lower is better)
        cost_per_cb = None
        cost_per_cb_bid = None
        if total_score and dph > 0:
            cost_per_cb = (dph / total_score) * 1000  # $/1000 CB points/hr
        if total_score and min_bid > 0:
            cost_per_cb_bid = (min_bid / total_score) * 1000
        
        instance = VastInstance(
            id=offer.get("id", 0),
            cpu_name=cpu_name,
            cpu_cores=vast_cores,
            cpu_cores_effective=offer.get("cpu_cores_effective", 0),
            cpu_ghz=offer.get("cpu_ghz", 0),
            dph_total=dph,
            min_bid=min_bid,
            ram_gb=offer.get("cpu_ram", 0) / 1024 if offer.get("cpu_ram") else 0,
            disk_gb=offer.get("disk_space", 0),
            reliability=offer.get("reliability2", 0),
            inet_down=offer.get("inet_down", 0),
            inet_up=offer.get("inet_up", 0),
            cuda_max_good=offer.get("cuda_max_good", 0),
            cpu_arch=offer.get("cpu_arch", ""),
            rentable=offer.get("rentable", False),
            cinebench_match=matched_name,
            cpu_spec_cores=spec_cores,
            socket_count=socket_count,
            cinebench_score_single=cb_score,
            cinebench_score_total=total_score,
            cost_per_cinebench_point=cost_per_cb,
            cost_per_cinebench_point_bid=cost_per_cb_bid
        )
        instances.append(instance)
    
    return instances


def print_results(instances: list[VastInstance], top_n: int = 25, min_reliability: float = 0.9, show_bids: bool = False):
    """Print the most cost-efficient instances."""
    
    scored = [i for i in instances 
              if i.cost_per_cinebench_point is not None 
              and i.reliability >= min_reliability]
    
    # Sort by bid price if showing bids, otherwise by on-demand price
    if show_bids:
        # Filter to instances with bid pricing and sort by bid efficiency
        scored = [i for i in scored if i.cost_per_cinebench_point_bid is not None]
        scored.sort(key=lambda x: x.cost_per_cinebench_point_bid)
    else:
        scored.sort(key=lambda x: x.cost_per_cinebench_point)
    
    print("\n" + "="*160)
    price_type = "INTERRUPTIBLE (BID)" if show_bids else "ON-DEMAND"
    print(f"VAST.AI CPU COST EFFICIENCY RANKING - {price_type} (by $/Cinebench R23 Multi-Thread Score)")
    print(f"Showing top {top_n} instances with reliability >= {min_reliability*100:.0f}%")
    print("="*160)
    
    if show_bids:
        print(f"\n{'Rank':<5} {'ID':<8} {'$/1k CB(bid)':<14} {'Bid$/hr':<9} {'On-Dem$/hr':<11} {'Sockets':<8} {'Total CB':<12} {'CPU':<28} {'Cores':<9} {'RAM':<7} {'Rel%':<5}")
        print("-"*160)
        for i, inst in enumerate(scored[:top_n], 1):
            socket_str = f"{inst.socket_count}x" if inst.socket_count > 1 else "1x"
            cpu_display = inst.cinebench_match[:27] if inst.cinebench_match else "Unknown"
            
            # Format cores as "Alloc/Total" if partial, otherwise just "Total"
            core_str = str(inst.cpu_cores)
            if inst.cpu_cores_effective > 0 and inst.cpu_cores_effective < inst.cpu_cores:
                core_str = f"{int(inst.cpu_cores_effective)}/{inst.cpu_cores}"
                
            print(f"{i:<5} {inst.id:<8} ${inst.cost_per_cinebench_point_bid:.5f}    ${inst.min_bid:<8.3f} ${inst.dph_total:<10.3f} {socket_str:<8} {inst.cinebench_score_total:<12,} {cpu_display:<28} {core_str:<9} {inst.ram_gb:<6.0f}GB {inst.reliability*100:<4.0f}%")
    else:
        print(f"\n{'Rank':<5} {'ID':<8} {'$/1k CB/hr':<12} {'$/hr':<8} {'Sockets':<8} {'Total CB':<12} {'CPU':<32} {'Cores':<9} {'RAM':<8} {'Rel%':<6}")
        print("-"*142)
        for i, inst in enumerate(scored[:top_n], 1):
            socket_str = f"{inst.socket_count}x" if inst.socket_count > 1 else "1x"
            cpu_display = inst.cinebench_match[:31] if inst.cinebench_match else "Unknown"
            
            # Format cores as "Alloc/Total" if partial, otherwise just "Total"
            core_str = str(inst.cpu_cores)
            if inst.cpu_cores_effective > 0 and inst.cpu_cores_effective < inst.cpu_cores:
                core_str = f"{int(inst.cpu_cores_effective)}/{inst.cpu_cores}"
                
            print(f"{i:<5} {inst.id:<8} ${inst.cost_per_cinebench_point:.5f}   ${inst.dph_total:<7.3f} {socket_str:<8} {inst.cinebench_score_total:<12,} {cpu_display:<32} {core_str:<9} {inst.ram_gb:<7.0f}GB {inst.reliability*100:<5.0f}%")
    
    # Summary
    print("\n" + "-"*140)
    dual_socket = sum(1 for i in scored if i.socket_count >= 2)
    print(f"\nSUMMARY: {len(instances)} offers | {len(scored)} with known CPU | {dual_socket} dual-socket detected")
    
    # Unknown CPUs
    unknown = set(i.cpu_name for i in instances if not i.cinebench_score_total and i.cpu_name)
    if unknown:
        print(f"\nUnknown CPUs (top 10):")
        for cpu in sorted(unknown)[:10]:
            print(f"  - {cpu}")


def main():
    import argparse
    
    parser = argparse.ArgumentParser(description="Find cost-efficient Vast.ai instances by CPU performance")
    parser.add_argument("--api-key", "-k", help="Vast.ai API key (or set VAST_API_KEY env var)")
    parser.add_argument("--top", "-n", type=int, default=25, help="Number of results (default: 25)")
    parser.add_argument("--min-reliability", "-r", type=float, default=0.9, help="Min reliability 0-1 (default: 0.9)")
    parser.add_argument("--show-all", action="store_true", help="Show all (no reliability filter)")
    parser.add_argument("--show-bids", action="store_true", help="Show interruptible (bid) pricing instead of on-demand")
    parser.add_argument("--rentable-only", action="store_true", help="Only show rentable instances")
    parser.add_argument("--include-unverified", action="store_true", help="Include unverified hosts (higher risk, potentially lower price)")
    parser.add_argument("--min-cuda", type=float, default=0, help="Minimum CUDA version required (e.g., 12.0)")
    parser.add_argument("--cpu-arch", choices=["amd64", "arm64"], help="Filter by CPU architecture")
    
    args = parser.parse_args()
    
    # Get API key from args or environment
    api_key = args.api_key or os.environ.get("VAST_API_KEY")
    if not api_key:
        print("Warning: No API key provided. Set VAST_API_KEY or use --api-key")
    
    print("Fetching Vast.ai offers...")
    # Verified default is True, so we pass False if include_unverified is set?
    # Actually, if we want "unverified included", we might need to change logic.
    # The API 'verified' filter usually means "verified only". 
    # If we want ALL, we might need to remove that filter or set verified=False?
    # Let's assume verified=False means "don't filter by verified" or "show unverified"?
    # Based on previous code: "verified": {"eq": verified}
    # If verified is False, it will fetch UNverified only? Or verified=False means filter verified=False?
    # If we want BOTH, we need to adjust fetch_vast_offers logic or make two calls.
    # But often passing verified=False to Vast API might mean "is verified = false" (i.e. only garbage).
    # Wait, the vastai CLI uses filters. 
    # If we want everything, we should probably remove the "verified" filter from the query dict entirely.
    # But let's stick to the plan: pass verified=False and see. 
    # If verified=False fetches ONLY unverified, then we miss the verified ones.
    # We want "include". 
    # Let's simple pass verified=(not args.include_unverified) is risky if verified=False means "only unverified".
    # Let's check `fetch_vast_offers` implementation again.
    
    # Correction: I will update `fetch_vast_offers` in the next tool call to handle "include" properly 
    # if I realize verified=False is exclusive. 
    # For now, let's update call site.
    
    offers = fetch_vast_offers(api_key, verified=(not args.include_unverified))
    
    if not offers:
        print("No offers found.")
        return
    
    print(f"Found {len(offers)} offers, processing...")
    instances = process_offers(offers)
    
    # Apply template compatibility filters
    if args.rentable_only:
        instances = [i for i in instances if i.rentable]
        print(f"  -> {len(instances)} rentable")
    
    if args.min_cuda > 0:
        instances = [i for i in instances if i.cuda_max_good >= args.min_cuda]
        print(f"  -> {len(instances)} with CUDA >= {args.min_cuda}")
    
    if args.cpu_arch:
        instances = [i for i in instances if i.cpu_arch == args.cpu_arch]
        print(f"  -> {len(instances)} with {args.cpu_arch} architecture")
    
    min_rel = 0.0 if args.show_all else args.min_reliability
    print_results(instances, top_n=args.top, min_reliability=min_rel, show_bids=args.show_bids)


if __name__ == "__main__":
    main()