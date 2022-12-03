package com.howe.common.utils.captcha;

/**
 *
 */
public class Quant {

    /**
     * number of colours used
     */
    protected static final int NET_SIZE = 256;


    protected static final int PRIME_1 = 499;

    protected static final int PRIME_2 = 491;

    protected static final int PRIME_3 = 487;

    protected static final int PRIME_4 = 503;

    protected static final int MINPICTURE_BYTES = (3 * PRIME_4);



    /*
     * Program Skeleton ---------------- [select samplefac in range 1..30] [read image
     * from input file] pic = (unsigned char*) malloc(3*width*height);
     * initnet(pic,3*width*height,samplefac); learn(); unbiasnet(); [write output image
     * header, using writecolourmap(f)] inxbuild(); write output image using
     * inxsearch(b,g,r)
     */

    /*
     * Network Definitions -------------------
     */

    protected static final int MAX_NET_POS = (NET_SIZE - 1);


    protected static final int NET_BIASSHIFT = 4;


    protected static final int NCYCLES = 100;


    protected static final int INT_BIASSHIFT = 16;

    protected static final int INT_BIAS = (1 << INT_BIASSHIFT);

    protected static final int GAMMA_SHIFT = 10;

    protected static final int GAMMA = (1 << GAMMA_SHIFT);

    protected static final int BETA_SHIFT = 10;

    protected static final int BETA = (INT_BIAS >> BETA_SHIFT);

    protected static final int BETA_GAMMA = (INT_BIAS << (GAMMA_SHIFT - BETA_SHIFT));


    protected static final int initrad = (NET_SIZE >> 3);

    protected static final int radiusbiasshift = 6;

    protected static final int radiusbias = (((int) 1) << radiusbiasshift);

    protected static final int initradius = (initrad
            * radiusbias);

    protected static final int radiusdec = 30;


    protected static final int alphabiasshift = 10;

    protected static final int initalpha = (((int) 1) << alphabiasshift);

    protected int alphadec;


    protected static final int radbiasshift = 8;

    protected static final int radbias = (((int) 1) << radbiasshift);

    protected static final int alpharadbshift = (alphabiasshift + radbiasshift);

    protected static final int alpharadbias = (((int) 1) << alpharadbshift);

    /*
     * Types and Global Variables --------------------------
     */

    protected byte[] thepicture;

    protected int lengthcount;

    protected int samplefac;

    // typedef int pixel[4];
    protected int[][] network;

    protected int[] netindex = new int[256];


    protected int[] bias = new int[NET_SIZE];


    protected int[] freq = new int[NET_SIZE];

    protected int[] radpower = new int[initrad];


    /*
     * Initialise network in range (0,0,0) to (255,255,255) and set parameters
     * -----------------------------------------------------------------------
     */
    public Quant(byte[] thepic, int len, int sample) {

        int i;
        int[] p;

        thepicture = thepic;
        lengthcount = len;
        samplefac = sample;

        network = new int[NET_SIZE][];
        for (i = 0; i < NET_SIZE; i++) {
            network[i] = new int[4];
            p = network[i];
            p[0] = p[1] = p[2] = (i << (NET_BIASSHIFT + 8)) / NET_SIZE;
            freq[i] = INT_BIAS / NET_SIZE;
            bias[i] = 0;
        }
    }

    public byte[] colorMap() {
        byte[] map = new byte[3 * NET_SIZE];
        int[] index = new int[NET_SIZE];
        for (int i = 0; i < NET_SIZE; i++) {
            index[network[i][3]] = i;
        }
        int k = 0;
        for (int i = 0; i < NET_SIZE; i++) {
            int j = index[i];
            map[k++] = (byte) (network[j][0]);
            map[k++] = (byte) (network[j][1]);
            map[k++] = (byte) (network[j][2]);
        }
        return map;
    }

    /*
     * Insertion sort of network and building of netindex[0..255] (to do after unbias)
     * -------------------------------------------------------------------------------
     */
    public void inxbuild() {

        int i, j, smallpos, smallval;
        int[] p;
        int[] q;
        int previouscol, startpos;

        previouscol = 0;
        startpos = 0;
        for (i = 0; i < NET_SIZE; i++) {
            p = network[i];
            smallpos = i;
            smallval = p[1];

            for (j = i + 1; j < NET_SIZE; j++) {
                q = network[j];
                if (q[1] < smallval) {
                    smallpos = j;
                    smallval = q[1];
                }
            }
            q = network[smallpos];

            if (i != smallpos) {
                j = q[0];
                q[0] = p[0];
                p[0] = j;
                j = q[1];
                q[1] = p[1];
                p[1] = j;
                j = q[2];
                q[2] = p[2];
                p[2] = j;
                j = q[3];
                q[3] = p[3];
                p[3] = j;
            }

            if (smallval != previouscol) {
                netindex[previouscol] = (startpos + i) >> 1;
                for (j = previouscol + 1; j < smallval; j++) {
                    netindex[j] = i;
                }
                previouscol = smallval;
                startpos = i;
            }
        }
        netindex[previouscol] = (startpos + MAX_NET_POS) >> 1;
        for (j = previouscol + 1; j < 256; j++) {
            // really 256
            netindex[j] = MAX_NET_POS;
        }
    }

    /**
     * Main Learning Loop ------------------
     */
    public void learn() {

        int i, j, b, g, r;
        int radius, rad, alpha, step, delta, samplepixels;
        byte[] p;
        int pix, lim;

        if (lengthcount < MINPICTURE_BYTES) {
            samplefac = 1;
        }
        alphadec = 30 + ((samplefac - 1) / 3);
        p = thepicture;
        pix = 0;
        lim = lengthcount;
        samplepixels = lengthcount / (3 * samplefac);
        delta = samplepixels / NCYCLES;
        alpha = initalpha;
        radius = initradius;

        rad = radius >> radiusbiasshift;
        if (rad <= 1) {
            rad = 0;
        }
        for (i = 0; i < rad; i++) {
            radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad));
        }

        // fprintf(stderr,"beginning 1D learning: initial radius=%d\n", rad);

        if (lengthcount < MINPICTURE_BYTES) {
            step = 3;
        } else if ((lengthcount % PRIME_1) != 0) {
            step = 3 * PRIME_1;
        } else {
            if ((lengthcount % PRIME_2) != 0) {
                step = 3 * PRIME_2;
            } else {
                if ((lengthcount % PRIME_3) != 0) {
                    step = 3 * PRIME_3;
                } else {
                    step = 3 * PRIME_4;
                }
            }
        }

        i = 0;
        while (i < samplepixels) {
            b = (p[pix + 0] & 0xff) << NET_BIASSHIFT;
            g = (p[pix + 1] & 0xff) << NET_BIASSHIFT;
            r = (p[pix + 2] & 0xff) << NET_BIASSHIFT;
            j = contest(b, g, r);

            altersingle(alpha, j, b, g, r);
            if (rad != 0) {
                alterneigh(rad, j, b, g, r);
            }

            pix += step;
            if (pix >= lim) {
                pix -= lengthcount;
            }

            i++;
            if (delta == 0) {
                delta = 1;
            }
            if (i % delta == 0) {
                alpha -= alpha / alphadec;
                radius -= radius / radiusdec;
                rad = radius >> radiusbiasshift;
                if (rad <= 1) {
                    rad = 0;
                }
                for (j = 0; j < rad; j++) {
                    radpower[j] = alpha * (((rad * rad - j * j) * radbias) / (rad * rad));
                }
            }
        }
        // fprintf(stderr,"finished 1D learning: final alpha=%f
        // !\n",((float)alpha)/initalpha);
    }

    /*
     * Search for BGR values 0..255 (after net is unbiased) and return colour index
     * ----------------------------------------------------------------------------
     */
    public int map(int b, int g, int r) {

        int i, j, dist, a, bestd;
        int[] p;
        int best;

        bestd = 1000;
        best = -1;
        i = netindex[g];
        j = i - 1;

        while ((i < NET_SIZE) || (j >= 0)) {
            if (i < NET_SIZE) {
                p = network[i];
                dist = p[1] - g;
                if (dist >= bestd) {
                    i = NET_SIZE;
                } else {
                    i++;
                    if (dist < 0) {
                        dist = -dist;
                    }
                    a = p[0] - b;
                    if (a < 0) {
                        a = -a;
                    }
                    dist += a;
                    if (dist < bestd) {
                        a = p[2] - r;
                        if (a < 0) {
                            a = -a;
                        }
                        dist += a;
                        if (dist < bestd) {
                            bestd = dist;
                            best = p[3];
                        }
                    }
                }
            }
            if (j >= 0) {
                p = network[j];
                // inx key - reverse dif
                dist = g - p[1];
                if (dist >= bestd) {
                    // stop iter
                    j = -1;
                } else {
                    j--;
                    if (dist < 0) {
                        dist = -dist;
                    }
                    a = p[0] - b;
                    if (a < 0) {
                        a = -a;
                    }
                    dist += a;
                    if (dist < bestd) {
                        a = p[2] - r;
                        if (a < 0) {
                            a = -a;
                        }
                        dist += a;
                        if (dist < bestd) {
                            bestd = dist;
                            best = p[3];
                        }
                    }
                }
            }
        }
        return (best);
    }

    public byte[] process() {
        learn();
        unbiasnet();
        inxbuild();
        return colorMap();
    }

    /*
     * Unbias network to give byte values 0..255 and record position i to prepare for sort
     * -----------------------------------------------------------------------------------
     */
    public void unbiasnet() {

        int i, j;

        for (i = 0; i < NET_SIZE; i++) {
            network[i][0] >>= NET_BIASSHIFT;
            network[i][1] >>= NET_BIASSHIFT;
            network[i][2] >>= NET_BIASSHIFT;
            network[i][3] = i;
        }
    }

    /*
     * Move adjacent neurons by precomputed alpha*(1-((i-j)^2/[r]^2)) in radpower[|i-j|]
     * ---------------------------------------------------------------------------------
     */
    protected void alterneigh(int rad, int i, int b, int g, int r) {

        int j, k, lo, hi, a, m;
        int[] p;

        lo = i - rad;
        if (lo < -1) {
            lo = -1;
        }
        hi = i + rad;
        if (hi > NET_SIZE) {
            hi = NET_SIZE;
        }

        j = i + 1;
        k = i - 1;
        m = 1;
        while ((j < hi) || (k > lo)) {
            a = radpower[m++];
            if (j < hi) {
                p = network[j++];
                try {
                    p[0] -= (a * (p[0] - b)) / alpharadbias;
                    p[1] -= (a * (p[1] - g)) / alpharadbias;
                    p[2] -= (a * (p[2] - r)) / alpharadbias;
                } catch (Exception e) {
                } // prevents 1.3 miscompilation
            }
            if (k > lo) {
                p = network[k--];
                try {
                    p[0] -= (a * (p[0] - b)) / alpharadbias;
                    p[1] -= (a * (p[1] - g)) / alpharadbias;
                    p[2] -= (a * (p[2] - r)) / alpharadbias;
                } catch (Exception e) {
                }
            }
        }
    }

    /*
     * Move neuron i towards biased (b,g,r) by factor alpha
     * ----------------------------------------------------
     */
    protected void altersingle(int alpha, int i, int b, int g, int r) {


        int[] n = network[i];
        n[0] -= (alpha * (n[0] - b)) / initalpha;
        n[1] -= (alpha * (n[1] - g)) / initalpha;
        n[2] -= (alpha * (n[2] - r)) / initalpha;
    }

    /*
     * Search for biased BGR values ----------------------------
     */
    protected int contest(int b, int g, int r) {


        int i, dist, a, biasdist, betafreq;
        int bestpos, bestbiaspos, bestd, bestbiasd;
        int[] n;

        bestd = ~(((int) 1) << 31);
        bestbiasd = bestd;
        bestpos = -1;
        bestbiaspos = bestpos;

        for (i = 0; i < NET_SIZE; i++) {
            n = network[i];
            dist = n[0] - b;
            if (dist < 0) {
                dist = -dist;
            }
            a = n[1] - g;
            if (a < 0) {
                a = -a;
            }
            dist += a;
            a = n[2] - r;
            if (a < 0) {
                a = -a;
            }
            dist += a;
            if (dist < bestd) {
                bestd = dist;
                bestpos = i;
            }
            biasdist = dist - ((bias[i]) >> (INT_BIASSHIFT - NET_BIASSHIFT));
            if (biasdist < bestbiasd) {
                bestbiasd = biasdist;
                bestbiaspos = i;
            }
            betafreq = (freq[i] >> BETA_SHIFT);
            freq[i] -= betafreq;
            bias[i] += (betafreq << GAMMA_SHIFT);
        }
        freq[bestpos] += BETA;
        bias[bestpos] -= BETA_GAMMA;
        return (bestbiaspos);
    }

}
